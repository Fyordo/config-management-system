package com.fyordo.cms.adminapi.service

import com.fyordo.cms.adminapi.config.ClusterProperties
import com.fyordo.cms.adminapi.dto.ClusterStatus
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
    private val clusterProperties: ClusterProperties,
    private val objectMapper: ObjectMapper
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    suspend fun getClustersStatuses(): Map<String, ClusterStatus> {
        return coroutineScope {
            buildMap {
                clusterProperties.urls.map { (cluster, endpoint) ->
                    async {
                        val clusterStatus = fetchClusterStatus(cluster, endpoint)
                        put(cluster, clusterStatus)
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun fetchClusterStatus(cluster: String, host: String): ClusterStatus {
        val url = "$host/raft/status"
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
                val type = object : TypeReference<ClusterStatus>() {}
                objectMapper.readValue(response.body(), type)
            } else {
                logger.warn { "Cluster $cluster at $url returned HTTP ${response.statusCode()}" }
                errorStatus(cluster, "HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch status from cluster $cluster at $url" }
            errorStatus(cluster, e.message ?: "Unknown error")
        }
    }

    private fun errorStatus(cluster: String, error: String): ClusterStatus = ClusterStatus(
        cluster,
        emptyList(),
        error
    )
}
