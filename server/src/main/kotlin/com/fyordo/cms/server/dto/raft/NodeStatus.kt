package com.fyordo.cms.server.dto.raft

data class NodeStatus(
    val nodeId: String,
    val isLeader: Boolean,
    val groupId: String,
)

data class NodeFullStatus(
    val nodeId: String,
    val isLeader: Boolean,
    val groupId: String?,
    val reachable: Boolean,
    val error: String?,
)
