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
}