package com.fyordo.cms.server.dto.raft

data class ClusterStatus(
    val groupId: String,
    val nodes: List<NodeFullStatus>
)
