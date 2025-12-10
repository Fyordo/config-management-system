package com.fyordo.cms.server.dto

import com.fyordo.cms.server.dto.property.PropertyKey

data class RaftCommand(
    override val version: Byte = 1,
    val operation: RaftOp,
    val key: PropertyKey,
    val value: ByteArray
) : Versioned {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RaftCommand) return false

        if (version != other.version) return false
        if (operation != other.operation) return false
        if (key != other.key) return false
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result: Int = version.toInt()
        result = 31 * result + operation.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }
}

enum class RaftOp(val value: Byte) {
    GET(1), PUT(2), DELETE(3)
}
