package com.fyordo.cms.adminapi.rest.v1

import com.fyordo.cms.adminapi.dto.ClusterStatus
import com.fyordo.cms.adminapi.service.RaftClusterService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/cluster")
class ClusterManagementController(
    private val clusterService: RaftClusterService
) {
    @GetMapping("/status")
    suspend fun getStatuses(): Map<String, ClusterStatus> {
        return clusterService.getClustersStatuses()
    }
}