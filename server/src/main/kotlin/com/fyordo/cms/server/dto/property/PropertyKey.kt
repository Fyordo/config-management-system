package com.fyordo.cms.server.dto.property

import com.fyordo.cms.server.dto.Versioned

data class PropertyKey(
    override val version: Byte = 1,
    val namespace: String,
    val service: String,
    val appId: String,
    val key: String
) : Versioned {
    companion object {
        fun fromString(value: String): PropertyKey {
            val parts = value.split("/")
            if (parts.size != 5) {
                throw IllegalArgumentException("Invalid format [$value] for version 1")
            }

            return PropertyKey(
                parts[0].toByte(),
                parts[1],
                parts[2],
                parts[3],
                parts[4]
            )
        }
    }

    override fun toString(): String {
        return "$version/$namespace/$service/$appId/$key"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PropertyKey) return false

        if (version != other.version) return false
        if (namespace != other.namespace) return false
        if (service != other.service) return false
        if (appId != other.appId) return false
        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        var result = namespace.hashCode()
        result = 31 * result + service.hashCode()
        result = 31 * result + appId.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + version.hashCode()
        return result
    }
}