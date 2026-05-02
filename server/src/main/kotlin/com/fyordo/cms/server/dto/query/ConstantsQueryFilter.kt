package com.fyordo.cms.server.dto.query

data class ConstantsQueryFilter(
    val namespaceRegex: String? = null,

    val serviceRegex: String? = null,

    val appIdRegex: String? = null
)
