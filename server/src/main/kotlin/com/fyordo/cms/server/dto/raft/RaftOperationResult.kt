package com.fyordo.cms.server.dto.raft

sealed class RaftOperationResult {

    data class Success(val data: ByteArray) : RaftOperationResult() {
        override fun isSuccess() = true

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success

            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    data class Error(val message: String, val cause: Throwable? = null) : RaftOperationResult() {
        override fun isSuccess() = false
    }

    abstract fun isSuccess(): Boolean
}
