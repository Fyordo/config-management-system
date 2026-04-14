package com.fyordo.cms.adminapi.dto.audit

import jakarta.validation.constraints.NotBlank
import java.io.Serializable

data class CreateUserAuditDto(
    @field:NotBlank val userId: String,
    @field:NotBlank val namespace: String,
    @field:NotBlank val service: String,
    @field:NotBlank val appId: String,
    @field:NotBlank val key: String,
    val prevValue: String? = null,
    val newValue: String? = null
) : Serializable