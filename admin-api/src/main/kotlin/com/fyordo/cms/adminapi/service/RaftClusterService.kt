package com.fyordo.cms.adminapi.service

import com.fyordo.cms.adminapi.dto.ClusterFullStatus
import com.fyordo.cms.adminapi.dto.ClusterStatus
import com.fyordo.cms.adminapi.dto.cluster.ClusterDto
import com.fyordo.cms.adminapi.entity.Cluster
import com.fyordo.cms.adminapi.repository.ClusterRepository
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
class RaftClusterService(
    private val objectMapper: ObjectMapper,
    private val unitDao: ClusterRepository
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    fun getClusterNames(): List<ClusterDto> {
        return unitDao.findAll().map { e ->
            ClusterDto(
                e.id,
                e.title,
                e.color,
                e.raftAddress,
            )
        }
    }

    suspend fun getClustersStatuses(): Map<String, ClusterFullStatus> {
        return coroutineScope {
            buildMap {
                unitDao.findAll().map { unit ->
                    async {
                        val clusterStatus = fetchClusterStatus(unit)
                        put(unit.title, clusterStatus)
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun fetchClusterStatus(cluster: Cluster): ClusterFullStatus {
        val url = "${cluster.raftAddress}/raft/status"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }

            if (response.statusCode() == 200) {
                val type = object : TypeReference<ClusterStatus>() {}
                val clusterStatus = objectMapper.readValue(response.body(), type)
                ClusterFullStatus(
                    clusterStatus.groupId,
                    clusterStatus.nodes,
                    clusterStatus.error,
                    cluster.color
                )
            } else {
                logger.warn { "Cluster ${cluster.title} at $url returned HTTP ${response.statusCode()}" }
                errorStatus(cluster.title, "HTTP ${response.statusCode()}", cluster.color)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch status from cluster ${cluster.title} at $url" }
            errorStatus(cluster.title, e.message ?: "Unknown error", cluster.color)
        }
    }

    private fun errorStatus(cluster: String, error: String, color: String): ClusterFullStatus = ClusterFullStatus(
        cluster,
        emptyList(),
        error,
        color
    )
}
