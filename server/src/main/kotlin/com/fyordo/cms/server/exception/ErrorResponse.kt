package com.fyordo.cms.server.exception

import java.time.Instant

data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val message: String? = null,
    val path: String? = null
)
