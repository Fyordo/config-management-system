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

    val electionTimeoutMs: Long = 3_000,

    val heartbeatIntervalMs: Long = 1_000,

    val requestIntervalMs: Long = 1_000,

    val peers: List<String> = emptyList(),

    val segmentSizeMax: String = "8MB",

    val preAllocatedSize: String = "4MB",

    val autoTriggerThreshold: Long = 10_000,

    val clusterMessageTimeoutMs: Long = 30_000,

    /** HTTP port of peer REST APIs (for cluster status aggregation). */
    val peerHttpPort: Int = 8080,
)