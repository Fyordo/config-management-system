package com.fyordo.cms.server.service.raft

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.config.props.RaftConfiguration
import com.fyordo.cms.server.dto.raft.RaftOperationResult
import com.fyordo.cms.server.serialization.raft.serializeRaftCommand
import com.fyordo.cms.server.utils.raft.parsePeers
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import org.apache.ratis.client.RaftClient
import org.apache.ratis.conf.RaftProperties
import org.apache.ratis.protocol.*
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString
import org.springframework.stereotype.Service
import java.net.InetSocketAddress
import java.util.*

private val logger = KotlinLogging.logger {}
private const val PEERS_PARTS_DELIMITER = ':'

@Service
class RaftClientFacade(
    private val raftProps: RaftConfiguration
) {
    private lateinit var raftClient: RaftClient

    @PostConstruct
    fun init() {
        logger.info { "Initializing RAFT client..." }

        try {
            // Validate peer configuration
            validatePeerConfiguration()

            val groupId = RaftGroupId.valueOf(UUID.nameUUIDFromBytes(raftProps.groupId.toByteArray()))

            val raftGroup = buildPeersList()
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

    private fun initClient(raftGroup: RaftGroup) {
        raftClient = RaftClient.newBuilder()
            .setClientId(ClientId.randomId())
            .setRaftGroup(raftGroup)
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
            val serialized = serializeRaftCommand(command)
            val response = withTimeout(raftProps.clusterMessageTimeoutMs) {
                withContext(Dispatchers.IO) {
                    val reply = raftClient.io().send(Message.valueOf(ByteString.copyFrom(serialized)))
                    reply.message.content.toByteArray()
                }
            }
            RaftOperationResult.Success(response)
        } catch (e: TimeoutCancellationException) {
            logger.error(e) { "Timeout sending command: $command (timeout: ${raftProps.clusterMessageTimeoutMs}ms)" }
            RaftOperationResult.Error("Command timeout after ${raftProps.clusterMessageTimeoutMs}ms", e)
        } catch (e: Exception) {
            logger.error(e) { "Error sending command: $command" }
            RaftOperationResult.Error("Failed to send command: ${e.message}", e)
        }
    }


    suspend fun sendQuery(command: CmsProto.RaftCommand): RaftOperationResult {
        return try {
            val serialized = serializeRaftCommand(command)
            val response = withTimeout(raftProps.clusterMessageTimeoutMs) {
                withContext(Dispatchers.IO) {
                    val reply = raftClient.io().sendReadOnly(Message.valueOf(ByteString.copyFrom(serialized)))
                    reply.message.content.toByteArray()
                }
            }
            RaftOperationResult.Success(response)
        } catch (e: TimeoutCancellationException) {
            logger.error(e) { "Timeout sending query: $command (timeout: ${raftProps.clusterMessageTimeoutMs}ms)" }
            RaftOperationResult.Error("Query timeout after ${raftProps.clusterMessageTimeoutMs}ms", e)
        } catch (e: Exception) {
            logger.error(e) { "Error sending query: $command" }
            RaftOperationResult.Error("Failed to send query: ${e.message}", e)
        }
    }
}

