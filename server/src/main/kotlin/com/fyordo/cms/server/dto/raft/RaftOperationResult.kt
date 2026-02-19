package com.fyordo.cms.server.dto.raft

sealed class RaftOperationResult {
    data class Success(val data: String) : RaftOperationResult() {
        override fun isSuccess() = true
    }

    data class Error(val message: String, val cause: Throwable? = null) : RaftOperationResult() {
        override fun isSuccess() = false
    }

    abstract fun isSuccess(): Boolean

    fun isError(): Boolean = !isSuccess()

    fun getOrThrow(): String = when (this) {
        is Success -> data
        is Error -> throw cause ?: RuntimeException(message)
    }

    fun getOrDefault(default: String): String = when (this) {
        is Success -> data
        is Error -> default
    }

    fun getOrNull(): String? = when (this) {
        is Success -> data
        is Error -> null
    }
}
