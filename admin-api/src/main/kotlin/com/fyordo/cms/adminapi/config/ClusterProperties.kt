package com.fyordo.cms.adminapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.cms.cluster")
data class ClusterProperties(
    val urls: Map<String, String> // CLUSTER_NAME -> http://cluster.url
)
