package com.fyordo.cms.adminapi.dto.audit

import jakarta.validation.constraints.NotBlank
import java.io.Serializable

/**
 * DTO for {@link com.fyordo.cms.adminapi.entity.UserAudit}
 */
data class CreateUserAuditDto(
    val userId: String,
    val namespace: String,
    val service: String,
    val appId: String,
    val key: String,
    val prevValue: String? = null,
    val newValue: String? = null
) : Serializable