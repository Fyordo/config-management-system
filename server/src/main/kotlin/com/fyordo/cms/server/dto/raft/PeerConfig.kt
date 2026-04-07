package com.fyordo.cms.server.dto.raft

data class PeerConfig(
    val nodeId: String,
    val host: String,
    val apiPort: Int
)
