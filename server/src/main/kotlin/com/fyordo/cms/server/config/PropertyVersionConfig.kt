package com.fyordo.cms.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "app.property")
data class PropertyVersionConfig(
    val currentVersion: Byte = 1,
    val maxValueSizeBytes: Int = 1024 * 1024 // 1MB default
)
