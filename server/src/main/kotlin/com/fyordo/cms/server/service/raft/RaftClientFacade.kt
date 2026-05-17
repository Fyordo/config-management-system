package com.fyordo.cms.server.service.raft

import com.fyordo.cms.CmsDtos
import com.fyordo.cms.server.config.props.RaftConfiguration
import com.fyordo.cms.server.dto.raft.RaftOperationResult
import com.fyordo.cms.server.serialization.raft.serializeRaftCommand
import com.fyordo.cms.server.utils.raft.parsePeers
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import org.apache.ratis.client.RaftClient
import org.apache.ratis.client.RaftClientConfigKeys
import org.apache.ratis.conf.RaftProperties
import org.apache.ratis.protocol.*
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString
import org.apache.ratis.util.TimeDuration
import org.springframework.stereotype.Service
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}
private const val PEERS_PARTS_DELIMITER = ':'

@Service
class RaftClientFacade(
    private val raftProps: RaftConfiguration
) {
    private lateinit var raftGroup: RaftGroup

    private val poolSize = maxOf(1, raftProps.clientPoolSize)
    private val pool = arrayOfNulls<RaftClient>(poolSize)
    private val poolLocks = Array(poolSize) { ReentrantLock() }
    private val roundRobin = AtomicInteger(0)

    private val raftDispatcher = Executors.newFixedThreadPool(32)
        .asCoroutineDispatcher()

    @PostConstruct
    fun init() {
        logger.info { "Initializing RAFT client pool (size=$poolSize)..." }

        try {
            validatePeerConfiguration()

            val groupId = RaftGroupId.valueOf(UUID.nameUUIDFromBytes(raftProps.groupId.toByteArray()))
            raftGroup = buildPeersList()
                .let { peers ->
                    if (peers.isEmpty()) throw IllegalStateException("No valid RAFT peers configured")
                    RaftGroup.valueOf(groupId, peers)
                }

            repeat(poolSize) { i -> pool[i] = buildClient() }

            logger.info { "RAFT client pool initialized ($poolSize clients)" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize RAFT client pool" }
            throw e
        }
    }

    private fun buildClient(): RaftClient {
        val properties = RaftProperties()
        RaftClientConfigKeys.Async.setOutstandingRequestsMax(
            properties,
            raftProps.clientOutstandingRequestsMax
        )
        RaftClientConfigKeys.Rpc.setRequestTimeout(
            properties,
            TimeDuration.valueOf(raftProps.clientRequestTimeoutMs, TimeUnit.MILLISECONDS)
        )

        return RaftClient.newBuilder()
            .setClientId(ClientId.randomId())
            .setRaftGroup(raftGroup)
            .setProperties(properties)
            .build()
            .also { logger.debug { "RAFT client created" } }
    }

    private fun reconnectSlot(index: Int) {
        poolLocks[index].withLock {
            logger.warn { "Reconnecting RAFT client slot $index..." }
            try {
                runCatching { pool[index]?.close() }
                pool[index] = buildClient()
                logger.info { "RAFT client slot $index reconnected" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to reconnect RAFT client slot $index" }
                throw e
            }
        }
    }

    private fun pickSlot(): Int = Math.floorMod(roundRobin.getAndIncrement(), poolSize)

    private fun isClosedError(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            val cls = current::class.simpleName ?: ""
            val msg = current.message ?: ""
            if (cls.contains("AlreadyClosed", ignoreCase = true) ||
                cls.contains("ClosedChannel", ignoreCase = true) ||
                msg.contains("already CLOSED", ignoreCase = true) ||
                msg.contains("is CLOSED", ignoreCase = true) ||
                msg.contains("Stream close", ignoreCase = true) ||
                msg.contains("channel is closed", ignoreCase = true)
            ) return true
            current = current.cause
        }
        return false
    }

    private fun commandContext(command: CmsDtos.RaftCommand): String {
        val key = command.key
        return "operation=${command.operation}, key=${key.namespace}/${key.service}/${key.appId}/${key.key}, version=${key.version}"
    }

    private fun validatePeerConfiguration() {
        raftProps.peers.forEach { peerConfig ->
            if (peerConfig.isNotBlank()) {
                val parts = peerConfig.split(PEERS_PARTS_DELIMITER)
                if (parts.size != 4) {
                    throw IllegalArgumentException(
                        "Invalid peer configuration format: '$peerConfig'. Expected format: 'nodeId:host:port'"
                    )
                }
                val port = parts[2].toIntOrNull()
                if (port == null || port <= 0 || port > 65535) {
                    throw IllegalArgumentException(
                        "Invalid peer port in configuration: '$peerConfig'. Port must be between 1 and 65535"
                    )
                }
            }
        }
    }

    private fun buildPeersList(): List<RaftPeer> = buildList {
        val localPeerId = RaftPeerId.valueOf(raftProps.nodeId)
        val localAddress = InetSocketAddress(raftProps.host, raftProps.port)
        add(
            RaftPeer.newBuilder()
                .setId(localPeerId)
                .setAddress(localAddress)
                .build()
        )

        raftProps.peers.forEach { peerConfig ->
            parsePeers(peerConfig, raftProps.nodeId)?.let { add(it) }
        }
    }

    @PreDestroy
    fun close() {
        logger.info { "Closing RAFT client pool..." }
        repeat(poolSize) { i ->
            runCatching { pool[i]?.close() }
                .onFailure { logger.error(it) { "Error closing RAFT client slot $i" } }
        }
        logger.info { "RAFT client pool closed" }
    }

    suspend fun sendCommand(command: CmsDtos.RaftCommand): RaftOperationResult = withContext(raftDispatcher) {
        val context = commandContext(command)
        val slot = pickSlot()
        try {
            doSendCommand(command, slot)
        } catch (e: TimeoutCancellationException) {
            logger.error(e) { "Timeout sending command: $context (timeout: ${raftProps.clusterMessageTimeoutMs}ms)" }
            RaftOperationResult.Error("Command timeout after ${raftProps.clusterMessageTimeoutMs}ms", e)
        } catch (e: CancellationException) {
            logger.warn { "RAFT command cancelled: $context (${e::class.simpleName}: ${e.message})" }
            RaftOperationResult.Error("RAFT command cancelled", e)
        } catch (e: Exception) {
            if (isClosedError(e)) {
                logger.warn { "RAFT client slot $slot stream closed, reconnecting and retrying..." }
                reconnectSlot(slot)
                try {
                    doSendCommand(command, slot)
                } catch (retryEx: Exception) {
                    logger.error(retryEx) { "Error sending command after reconnect on slot $slot: $context" }
                    RaftOperationResult.Error("Failed to send command: ${retryEx.message}", retryEx)
                }
            } else {
                logger.error(e) { "Error sending command on slot $slot: $context" }
                RaftOperationResult.Error("Failed to send command: ${e.message}", e)
            }
        }
    }

    private suspend fun doSendCommand(command: CmsDtos.RaftCommand, slot: Int): RaftOperationResult {
        val serialized = serializeRaftCommand(command)
        val client = pool[slot] ?: error("RAFT client slot $slot is null")
        val response = withTimeout(raftProps.clusterMessageTimeoutMs) {
            val reply = client.async().send(Message.valueOf(ByteString.copyFrom(serialized))).await()
            reply.message.content.toByteArray()
        }
        return RaftOperationResult.Success(response)
    }
}
