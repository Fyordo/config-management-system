package com.fyordo.cms.server.service.raft

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.fyordo.cms.server.config.props.RaftConfiguration
import com.fyordo.cms.server.utils.raft.parsePeerHosts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.stereotype.Service
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

    suspend fun getClusterStatus(): Map<String, Any?> {
        val localStatus = raftServer.getLocalNodeStatus()
        val groupId = localStatus["groupId"] as String

        val peerHosts = parsePeerHosts(
            peers = raftProps.peers,
            currentNodeId = raftProps.nodeId,
            currentNodeHost = raftProps.host,
        )

        val nodes = coroutineScope {
            peerHosts.map { (nodeId, host) ->
                async {
                    if (nodeId == raftProps.nodeId) {
                        localStatus + ("reachable" to true)
                    } else {
                        fetchPeerStatus(nodeId, host)
                    }
                }
            }.awaitAll()
        }

        return mapOf(
            "groupId" to groupId,
            "nodes" to nodes,
        )
    }

    private suspend fun fetchPeerStatus(nodeId: String, host: String): Map<String, Any?> {
        val url = "http://$host:${raftProps.peerHttpPort}/raft/status/local"
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
                val type = object : TypeReference<Map<String, Any?>>() {}
                val body: Map<String, Any?> = objectMapper.readValue(response.body(), type)
                body + ("reachable" to true)
            } else {
                logger.warn { "Peer $nodeId at $url returned HTTP ${response.statusCode()}" }
                errorStatus(nodeId, "HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch status from peer $nodeId at $url" }
            errorStatus(nodeId, e.message ?: "Unknown error")
        }
    }

    private fun errorStatus(nodeId: String, error: String): Map<String, Any?> = mapOf(
        "nodeId" to nodeId,
        "isLeader" to null,
        "groupId" to null,
        "reachable" to false,
        "error" to error,
    )
}
