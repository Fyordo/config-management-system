package com.fyordo.cms.adminapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.cms.web")
data class CorsProperties(
    val noCorsUrls: List<String> = emptyList()
)
