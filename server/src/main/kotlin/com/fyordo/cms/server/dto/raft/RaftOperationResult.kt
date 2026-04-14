package com.fyordo.cms.server.dto.raft

sealed class RaftOperationResult {
    data class Success(val data: ByteArray) : RaftOperationResult() {
        override fun isSuccess() = true
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
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
