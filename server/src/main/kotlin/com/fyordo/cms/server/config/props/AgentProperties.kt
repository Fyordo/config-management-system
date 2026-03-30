package com.fyordo.cms.server.config.props

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.server.agent")
data class AgentProperties(
    val agentHealthcheckPeriodMs: Long = 1_000L
)