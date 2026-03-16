package com.fyordo.cms.server.rest.v1

import com.fyordo.cms.server.dto.raft.ClusterStatus
import com.fyordo.cms.server.dto.raft.NodeStatus
import com.fyordo.cms.server.service.raft.RaftClusterStatusService
import com.fyordo.cms.server.service.raft.RaftServerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/raft")
class RaftController(
    private val server: RaftServerService,
    private val clusterStatus: RaftClusterStatusService,
) {
    @GetMapping("/status/local")
    fun getLocalStatus(): NodeStatus = server.getLocalNodeStatus()

    @GetMapping("/status")
    suspend fun getStatus(): ClusterStatus = clusterStatus.getClusterStatus()
}