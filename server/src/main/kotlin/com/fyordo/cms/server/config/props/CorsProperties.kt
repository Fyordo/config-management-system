package com.fyordo.cms.server.config.props

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.server.web")
data class CorsProperties(
    val noCorsUrls: List<String> = emptyList()
)