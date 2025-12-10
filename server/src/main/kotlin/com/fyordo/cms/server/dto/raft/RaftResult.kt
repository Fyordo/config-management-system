package com.fyordo.cms.server.dto.raft

import com.fyordo.cms.server.dto.Versioned

data class RaftResult(
    override val version: Byte = 1,
    val result: ByteArray,
    val status: RaftResultStatus
) : Versioned {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RaftResult) return false

        if (version != other.version) return false
        if (!result.contentEquals(other.result)) return false
        if (status != other.status) return false

        return true
    }

    override fun hashCode(): Int {
        var result1 = version.toInt()
        result1 = 31 * result1 + result.contentHashCode()
        result1 = 31 * result1 + status.hashCode()
        return result1
    }
}

enum class RaftResultStatus(val value: Byte) {
    OK(1),
    NOT_FOUND(2),
    ERROR(3);

    companion object {
        private val VALUES = RaftResultStatus.entries.associateBy(RaftResultStatus::value)

        fun getByValue(value: Byte): RaftResultStatus {
            return VALUES[value] ?: throw IllegalArgumentException("Unknown value $value for RaftResultStatus")
        }
    }
}