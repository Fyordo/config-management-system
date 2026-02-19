package com.fyordo.cms.server.dto.query

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class PropertyQueryFilter(
    val namespaceRegex: String? = null,

    val serviceRegex: String? = null,

    val appIdRegex: String? = null,

    val keyRegex: String? = null,

    val valueRegex: String? = null,

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 1000, message = "Limit cannot exceed 1000")
    val limit: Int = 10
)
