package com.fyordo.cms.adminapi.dto.audit

import java.io.Serializable
import java.time.Instant

data class UserAuditSearchFilter(
    val namespaceRegex: String? = null,
    val serviceRegex: String? = null,
    val appIdRegex: String? = null,
    val keyRegex: String? = null,
    val userId: String? = null,
    val after: Instant? = null,
    val before: Instant? = null,
) : Serializable
