package com.fyordo.cms.server.dto.query

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class ConstantsQueryFilter(
    val namespaceRegex: String? = null,

    val serviceRegex: String? = null,

    val appIdRegex: String? = null
)
