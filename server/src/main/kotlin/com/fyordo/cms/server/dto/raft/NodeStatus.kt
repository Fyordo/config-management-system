package com.fyordo.cms.server.dto.raft

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fyordo.cms.server.dto.grpc.AgentId

@JsonIgnoreProperties(ignoreUnknown = true)
data class NodeStatus(
    val nodeId: String,
    val isLeader: Boolean,
    val groupId: String,
    val connectedAgents: Set<AgentId> = emptySet(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NodeFullStatus(
    val nodeId: String,
    val isLeader: Boolean,
    val groupId: String?,
    val reachable: Boolean,
    val error: String?,
    val connectedAgents: Set<AgentId> = emptySet(),
)
