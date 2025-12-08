package com.fyordo.cms.server.config.props

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.server.rocksdb")
data class RocksDbConfig(
    val path: String,
    val compressionEnabled: Boolean = true,
    val maxBackgroundJobs: Int = 4,
    val maxOpenFiles: Int = 100,
)
