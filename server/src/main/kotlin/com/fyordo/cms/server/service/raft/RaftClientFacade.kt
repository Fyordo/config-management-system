package com.fyordo.cms.server.service.raft

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.config.props.RaftConfiguration
import com.fyordo.cms.server.dto.raft.RaftOperationResult
import com.fyordo.cms.server.serialization.raft.serializeRaftCommand
import com.fyordo.cms.server.utils.raft.parsePeers
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import org.apache.ratis.client.RaftClient
import org.apache.ratis.conf.RaftProperties
import org.apache.ratis.protocol.*
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString
import org.springframework.stereotype.Service
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}
private const val PEERS_PARTS_DELIMITER = ':'

@Service
class RaftClientFacade(
    private val raftProps: RaftConfiguration
) {
    private lateinit var raftClient: RaftClient
    private lateinit var raftGroup: RaftGroup
    private val clientLock = ReentrantLock()

    @PostConstruct
    fun init() {
        logger.info { "Initializing RAFT client..." }

        try {
            validatePeerConfiguration()

            val groupId = RaftGroupId.valueOf(UUID.nameUUIDFromBytes(raftProps.groupId.toByteArray()))

            raftGroup = buildPeersList()
                .let { peers ->
                    if (peers.isEmpty()) {
                        throw IllegalStateException("No valid RAFT peers configured")
                    }
                    RaftGroup.valueOf(groupId, peers)
                }

            initClient(raftGroup)
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize RAFT client" }
            throw e
        }
    }

    private fun reconnect() {
        clientLock.withLock {
            logger.warn { "Reconnecting RAFT client..." }
            try {
                if (::raftClient.isInitialized) {
                    runCatching { raftClient.close() }
                }
                initClient(raftGroup)
                logger.info { "RAFT client reconnected successfully" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to reconnect RAFT client" }
                throw e
            }
        }
    }

    private fun isClosedError(e: Throwable): Boolean {
        val msg = e.message ?: ""
        return msg.contains("already CLOSED", ignoreCase = true) ||
            msg.contains("is CLOSED", ignoreCase = true)
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

    private fun initClient(group: RaftGroup) {
        raftClient = RaftClient.newBuilder()
            .setClientId(ClientId.randomId())
            .setRaftGroup(group)
            .setProperties(RaftProperties())
            .build()
            .also {
                logger.info { "RAFT client initialized successfully" }
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
            parsePeers(
                peerConfig,
                raftProps.nodeId,
                raftProps.host,
                raftProps.port,
            )?.let { add(it) }
        }
    }

    @PreDestroy
    fun close() {
        logger.info { "Closing RAFT client..." }
        try {
            if (::raftClient.isInitialized) {
                raftClient.close()
                logger.info { "RAFT client closed successfully" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error closing RAFT client" }
        }
    }

    suspend fun sendCommand(command: CmsProto.RaftCommand): RaftOperationResult {
        return try {
            doSendCommand(command)
        } catch (e: TimeoutCancellationException) {
            logger.error(e) { "Timeout sending command: $command (timeout: ${raftProps.clusterMessageTimeoutMs}ms)" }
            RaftOperationResult.Error("Command timeout after ${raftProps.clusterMessageTimeoutMs}ms", e)
        } catch (e: Exception) {
            if (isClosedError(e)) {
                logger.warn { "RAFT client was CLOSED, reconnecting and retrying command..." }
                reconnect()
                return try {
                    doSendCommand(command)
                } catch (retryEx: Exception) {
                    logger.error(retryEx) { "Error sending command after reconnect: $command" }
                    RaftOperationResult.Error("Failed to send command: ${retryEx.message}", retryEx)
                }
            }
            logger.error(e) { "Error sending command: $command" }
            RaftOperationResult.Error("Failed to send command: ${e.message}", e)
        }
    }

    private suspend fun doSendCommand(command: CmsProto.RaftCommand): RaftOperationResult {
        val serialized = serializeRaftCommand(command)
        val response = withTimeout(raftProps.clusterMessageTimeoutMs) {
            val reply = raftClient.async().send(Message.valueOf(ByteString.copyFrom(serialized))).await()
            reply.message.content.toByteArray()
        }
        return RaftOperationResult.Success(response)
    }

    suspend fun sendQuery(command: CmsProto.RaftCommand): RaftOperationResult {
        return try {
            doSendQuery(command)
        } catch (e: TimeoutCancellationException) {
            logger.error(e) { "Timeout sending query: $command (timeout: ${raftProps.clusterMessageTimeoutMs}ms)" }
            RaftOperationResult.Error("Query timeout after ${raftProps.clusterMessageTimeoutMs}ms", e)
        } catch (e: Exception) {
            if (isClosedError(e)) {
                logger.warn { "RAFT client was CLOSED, reconnecting and retrying query..." }
                reconnect()
                return try {
                    doSendQuery(command)
                } catch (retryEx: Exception) {
                    logger.error(retryEx) { "Error sending query after reconnect: $command" }
                    RaftOperationResult.Error("Failed to send query: ${retryEx.message}", retryEx)
                }
            }
            logger.error(e) { "Error sending query: $command" }
            RaftOperationResult.Error("Failed to send query: ${e.message}", e)
        }
    }

    private suspend fun doSendQuery(command: CmsProto.RaftCommand): RaftOperationResult {
        val serialized = serializeRaftCommand(command)
        val response = withTimeout(raftProps.clusterMessageTimeoutMs) {
            val reply = raftClient.async().sendReadOnly(Message.valueOf(ByteString.copyFrom(serialized))).await()
            reply.message.content.toByteArray()
        }
        return RaftOperationResult.Success(response)
    }
}

