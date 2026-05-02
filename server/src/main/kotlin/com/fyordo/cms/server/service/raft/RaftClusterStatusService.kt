package com.fyordo.cms.server.service.raft

import com.fyordo.cms.server.config.props.RaftConfiguration
import com.fyordo.cms.server.dto.raft.ClusterStatus
import com.fyordo.cms.server.dto.raft.NodeFullStatus
import com.fyordo.cms.server.dto.raft.NodeStatus
import com.fyordo.cms.server.dto.raft.PeerConfig
import com.fyordo.cms.server.utils.raft.parsePeerHosts
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
class RaftClusterStatusService(
    private val raftProps: RaftConfiguration,
    private val raftServer: RaftServerService,
    private val objectMapper: ObjectMapper,
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    private val peerHosts: List<PeerConfig> by lazy {
        parsePeerHosts(
            peers = raftProps.peers,
            currentNodeId = raftProps.nodeId,
            currentNodeHost = raftProps.host,
            currentNodeApiPort = raftProps.peerHttpPort,
        )
    }

    suspend fun getClusterStatus(): ClusterStatus {
        val localStatus = raftServer.getLocalNodeStatus()
        val groupId = localStatus.groupId

        val nodes = coroutineScope {
            peerHosts.map { peer ->
                async {
                    if (peer.nodeId == raftProps.nodeId) {
                        NodeFullStatus(
                            localStatus.nodeId,
                            localStatus.isLeader,
                            localStatus.groupId,
                            true,
                            null,
                            localStatus.connectedAgents
                        )
                    } else {
                        fetchPeerStatus(peer)
                    }
                }
            }.awaitAll()
        }

        return ClusterStatus(
            groupId,
            nodes,
        )
    }

    private suspend fun fetchPeerStatus(peer: PeerConfig): NodeFullStatus {
        val url = "http://${peer.host}:${peer.apiPort}/raft/status/local"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }

            if (response.statusCode() == 200) {
                val type = object : TypeReference<NodeStatus>() {}
                val localStatus: NodeStatus = objectMapper.readValue(response.body(), type)
                NodeFullStatus(
                    localStatus.nodeId,
                    localStatus.isLeader,
                    localStatus.groupId,
                    true,
                    null,
                    localStatus.connectedAgents
                )
            } else {
                logger.warn { "Peer ${peer.nodeId} at $url returned HTTP ${response.statusCode()}" }
                errorStatus(peer.nodeId, "HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch status from peer ${peer.nodeId} at $url" }
            errorStatus(peer.nodeId, e.message ?: "Unknown error")
        }
    }

    private fun errorStatus(nodeId: String, error: String): NodeFullStatus = NodeFullStatus(
        nodeId,
        false,
        null,
        false,
        error,
    )
}
