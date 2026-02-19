package com.fyordo.cms.server.dto.grpc

data class AgentId(
    val namespace: String,
    val service: String,
    val appId: String,
)
