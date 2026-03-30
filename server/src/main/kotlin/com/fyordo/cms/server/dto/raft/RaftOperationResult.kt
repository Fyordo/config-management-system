package com.fyordo.cms.server.dto.raft

sealed class RaftOperationResult {
    data class Success(val data: ByteArray) : RaftOperationResult() {
        override fun isSuccess() = true
    }

    data class Error(val message: String, val cause: Throwable? = null) : RaftOperationResult() {
        override fun isSuccess() = false
    }

    abstract fun isSuccess(): Boolean

    fun isError(): Boolean = !isSuccess()

    fun getOrThrow(): ByteArray = when (this) {
        is Success -> data
        is Error -> throw cause ?: RuntimeException(message)
    }

    fun getOrDefault(default: ByteArray): ByteArray = when (this) {
        is Success -> data
        is Error -> default
    }

    fun getOrNull(): ByteArray? = when (this) {
        is Success -> data
        is Error -> null
    }
}
