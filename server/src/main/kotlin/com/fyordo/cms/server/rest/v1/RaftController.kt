package com.fyordo.cms.server.rest.v1

import com.fyordo.cms.server.service.raft.RaftServerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/raft")
class RaftController(
    private val server: RaftServerService
) {
    @GetMapping("/status")
    fun getStatus(): Map<String, Any> {
        return mapOf(
            "isLeader" to server.isLeader(),
            "leaderId" to (server.getLeaderId() ?: "unknown"),
            "groupId" to server.getGroupId().uuid.toString()
        )
    }
}