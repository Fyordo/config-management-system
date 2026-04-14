package com.fyordo.cms.adminapi.dto.audit

import java.io.Serializable
import java.sql.Timestamp
import java.util.*

data class UserAuditFullDto(
    val id: Long,
    val userId: String,
    val namespace: String,
    val service: String,
    val appId: String,
    val key: String,
    val prevValue: String? = null,
    val newValue: String? = null,
    val timestamp: Timestamp
) : Serializable