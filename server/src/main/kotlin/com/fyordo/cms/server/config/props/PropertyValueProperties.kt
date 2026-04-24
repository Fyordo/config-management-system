package com.fyordo.cms.server.config.props

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.property")
data class PropertyValueProperties(
    val currentVersion: Int = 1,
    val maxValueSizeBytes: Int = 1024 * 1024 // 1MB default
)