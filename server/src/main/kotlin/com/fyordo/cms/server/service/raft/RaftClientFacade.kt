package com.fyordo.cms.server.service.raft

import com.fyordo.cms.server.config.props.RaftConfiguration
import com.fyordo.cms.server.dto.raft.RaftCommand
import com.fyordo.cms.server.serialization.raft.serializeRaftCommand
import com.fyordo.cms.server.utils.raft.parsePeers
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.apache.ratis.client.RaftClient
import org.apache.ratis.conf.RaftProperties
import org.apache.ratis.protocol.*
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
            val groupId = RaftGroupId.valueOf(UUID.nameUUIDFromBytes(raftProps.groupId.toByteArray()))

            val raftGroup = buildPeersList()
                .let { peers ->
                    RaftGroup.valueOf(groupId, peers)
                }

            initClient(raftGroup)
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize RAFT client" }
            throw e
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

    suspend fun sendCommand(command: RaftCommand): String {
        return try {
            val serialized = serializeRaftCommand(command)
            withContext(Dispatchers.IO) {
                val reply = raftClient.io().send(Message.valueOf(serialized))
                reply.message.content.toStringUtf8()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error sending command: $command" }
            "ERROR: ${e.message}"
        }
    }

    suspend fun sendQuery(command: RaftCommand): String {
        return try {
            val serialized = serializeRaftCommand(command)
            withContext(Dispatchers.IO) {
                val reply = raftClient.io().sendReadOnly(Message.valueOf(serialized))
                reply.message.content.toStringUtf8()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error sending query: $command" }
            "ERROR: ${e.message}"
        }
    }
}

