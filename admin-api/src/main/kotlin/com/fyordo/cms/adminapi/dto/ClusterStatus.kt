package com.fyordo.cms.adminapi.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClusterStatus(
    val groupId: String?,
    val nodes: List<NodeStatus>,
    val error: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClusterFullStatus(
    val groupId: String?,
    val nodes: List<NodeStatus>,
    val error: String?,
    val color: String
)
