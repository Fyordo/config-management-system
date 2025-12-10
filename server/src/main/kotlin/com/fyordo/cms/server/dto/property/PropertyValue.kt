package com.fyordo.cms.server.dto.property

import com.fyordo.cms.server.dto.Versioned

data class PropertyValue(
    override val version: Byte = 1,
    val value: ByteArray,
    val lastModifiedMs: Long
): Versioned {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PropertyValue) return false

        if (version != other.version) return false
        if (lastModifiedMs != other.lastModifiedMs) return false
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lastModifiedMs.hashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + version.hashCode()
        return result
    }
}
