package com.fyordo.cms.adminapi.dto

data class NodeFullStatus(
    val nodeId: String,
    val isLeader: Boolean,
    val groupId: String?,
    val reachable: Boolean,
    val error: String?,
)
