package com.fyordo.cms.adminapi.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class AgentId(
    val namespace: String,
    val service: String,
    val appId: String,
)
