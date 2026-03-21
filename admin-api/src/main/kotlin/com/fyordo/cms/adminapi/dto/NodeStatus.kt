package com.fyordo.cms.adminapi.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class NodeStatus(
    val nodeId: String,
    val isLeader: Boolean,
    val groupId: String?,
    val reachable: Boolean,
    val error: String?,
    val connectedAgents: Set<AgentId> = emptySet(),
)
