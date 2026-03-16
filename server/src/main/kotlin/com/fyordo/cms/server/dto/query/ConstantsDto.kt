package com.fyordo.cms.server.dto.query

data class ConstantsDto(
    val namespaces: Set<String>,
    val services: Set<String>,
    val appIds: Set<String>
)
