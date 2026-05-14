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

    val requestIntervalMs: Long = 5_000,

    val peers: List<String> = emptyList(),

    val segmentSizeMax: String = "8MB",

    val preAllocatedSize: String = "4MB",

    val autoTriggerThreshold: Long = 10_000,

    val clusterMessageTimeoutMs: Long = 30_000,

    /** HTTP port of peer REST APIs (for cluster status aggregation). */
    val peerHttpPort: Int = 8080,

    val leaderOutstandingAppendsMax: Int = 128,

    val clientPoolSize: Int = 4,

    /**
     * Max number of outstanding requests per RaftClient.
     * Ratis default is 100, which is too low for high RPS.
     * When this limit is reached, client.async().send() BLOCKS the calling thread.
     */
    val clientOutstandingRequestsMax: Int = 5000,

    /**
     * Ratis client-side request timeout.
     * Ratis default is 3s.
     */
    val clientRequestTimeoutMs: Long = 5_000,
)