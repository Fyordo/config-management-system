package com.fyordo.cms.server.service.raft

import com.fyordo.cms.server.config.props.RaftConfiguration
import com.fyordo.cms.server.utils.raft.parsePeers
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import mu.KotlinLogging
import org.apache.ratis.conf.RaftProperties
import org.apache.ratis.grpc.GrpcConfigKeys
import org.apache.ratis.protocol.RaftGroup
import org.apache.ratis.protocol.RaftGroupId
import org.apache.ratis.protocol.RaftPeer
import org.apache.ratis.protocol.RaftPeerId
import org.apache.ratis.server.RaftServer
import org.apache.ratis.server.RaftServerConfigKeys
import org.apache.ratis.util.SizeInBytes
import org.apache.ratis.util.TimeDuration
import org.springframework.stereotype.Service
import java.io.File
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}
private const val UNKNOWN = "<UNKNOWN>"

@Service
class RaftServerService(
    private val raftProps: RaftConfiguration,
    private val raftStateMachine: RaftStateMachine
) {
    private lateinit var raftServer: RaftServer
    private lateinit var raftGroupId: RaftGroupId

    @PostConstruct
    fun init() {
        logger.info { "Starting RAFT server with configuration: $raftProps" }

        try {
            raftGroupId = RaftGroupId.valueOf(UUID.nameUUIDFromBytes(raftProps.groupId.toByteArray()))

            val ratisProperties = RaftProperties().apply {
                val storageDir = File(raftProps.storageDir)
                storageDir.mkdirs()
                RaftServerConfigKeys.setStorageDir(this, listOf(storageDir))

                RaftServerConfigKeys.Rpc.setTimeoutMin(
                    this,
                    TimeDuration.valueOf(raftProps.electionTimeoutMs, TimeUnit.MILLISECONDS)
                )
                RaftServerConfigKeys.Rpc.setTimeoutMax(
                    this,
                    TimeDuration.valueOf(raftProps.electionTimeoutMs * 2, TimeUnit.MILLISECONDS)
                )

                RaftServerConfigKeys.Rpc.setRequestTimeout(
                    this,
                    TimeDuration.valueOf(raftProps.heartbeatIntervalMs, TimeUnit.MILLISECONDS)
                )

                RaftServerConfigKeys.Log.setSegmentSizeMax(this, SizeInBytes.valueOf(raftProps.segmentSizeMax))
                RaftServerConfigKeys.Log.setPreallocatedSize(this, SizeInBytes.valueOf(raftProps.preAllocatedSize))

                RaftServerConfigKeys.Snapshot.setAutoTriggerEnabled(this, true)
                RaftServerConfigKeys.Snapshot.setAutoTriggerThreshold(this, raftProps.autoTriggerThreshold)

                // Настройка gRPC транспорта
                GrpcConfigKeys.Server.setPort(this, raftProps.port)
            }

            val localPeerId = RaftPeerId.valueOf(raftProps.nodeId)
            val localAddress = InetSocketAddress(raftProps.host, raftProps.port)
            val localPeer = RaftPeer.newBuilder()
                .setId(localPeerId)
                .setAddress(localAddress)
                .build()

            val allPeers = mutableListOf(localPeer)

            raftProps.peers.forEach { peerConfig ->
                parsePeers(
                    peerConfig,
                    raftProps.nodeId,
                    raftProps.host,
                    raftProps.port,
                )?.let { allPeers.add(it) }
            }

            logger.info { "RAFT peers configured: ${allPeers.map { it.id }}" }

            val raftGroup = RaftGroup.valueOf(raftGroupId, allPeers)

            raftServer = RaftServer.newBuilder()
                .setServerId(localPeerId)
                .setGroup(raftGroup)
                .setProperties(ratisProperties)
                .setStateMachine(raftStateMachine)
                .build()

            raftServer.start()

            logger.info { "RAFT server started successfully on ${raftProps.host}:${raftProps.port}" }
            logger.info { "RAFT group ID: ${raftGroupId.uuid}" }
            logger.info { "RAFT node ID: ${raftProps.nodeId}" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to start RAFT server" }
            throw e
        }
    }

    @PreDestroy
    fun stop() {
        logger.info { "Stopping RAFT server..." }
        try {
            if (::raftServer.isInitialized) {
                raftServer.close()
                logger.info { "RAFT server stopped successfully" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error stopping RAFT server" }
        }
    }

    fun getGroupId(): RaftGroupId = raftGroupId

    fun isLeader(): Boolean {
        return try {
            val divisionInfo = raftServer.getDivision(raftGroupId)?.info
            divisionInfo?.isLeader ?: false
        } catch (e: Exception) {
            logger.warn(e) { "Error checking leader status" }
            false
        }
    }

    fun getLeaderId(): String {
        return runCatching {
            val division = raftServer.getDivision(raftGroupId)
            val currentRole = division?.info?.currentRole

            if (division?.info?.isLeader == true) {
                raftProps.nodeId
            } else {
                currentRole?.toString() ?: UNKNOWN
            }
        }.onFailure {
            logger.warn(it) { "Error getting leader ID" }
            UNKNOWN
        }.getOrDefault(UNKNOWN)
    }
}

