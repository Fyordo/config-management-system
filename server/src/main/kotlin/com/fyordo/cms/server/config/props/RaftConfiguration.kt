package com.fyordo.cms.server.config.props

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.server.raft")
data class RaftConfiguration(
    /** ! Unique ! */
    val nodeId: String,

    val host: String = "localhost",

    val port: Int = 6000,

    val storageDir: String,

    val groupId: String = "cms-raft-group",

    val electionTimeoutMs: Long = 3000,

    val heartbeatIntervalMs: Long = 1000,

    val peers: List<String> = emptyList()
)