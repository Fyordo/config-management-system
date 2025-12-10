package com.fyordo.cms.server.dto.query

data class PropertyQueryFilter(
    val namespaceRegex: String? = null,
    val serviceRegex: String? = null,
    val appIdRegex: String? = null,
    val keyRegex: String? = null,
    val valueRegex: String? = null,
    val limit: Int = 10
)
