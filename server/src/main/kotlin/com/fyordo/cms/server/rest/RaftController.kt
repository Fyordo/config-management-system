package com.fyordo.cms.server.rest

import com.fyordo.cms.server.dto.RaftCommand
import com.fyordo.cms.server.dto.RaftOp
import com.fyordo.cms.server.service.raft.RaftClientFacade
import com.fyordo.cms.server.service.raft.RaftServerService
import mu.KotlinLogging
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * REST контроллер для тестирования RAFT кластера
 */
@RestController
@RequestMapping("/api/raft")
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