package com.fyordo.cms.adminapi.dto

data class ClusterStatus(
    val groupId: String?,
    val nodes: List<NodeFullStatus>,
    val error: String?,
)
