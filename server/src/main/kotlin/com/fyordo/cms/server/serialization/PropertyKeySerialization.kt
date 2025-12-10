package com.fyordo.cms.server.serialization

import com.fyordo.cms.server.dto.property.PropertyKey

private const val DELIMITER = "/"

fun serializePropertyKey(key: PropertyKey): String {
    return when(key.version.toInt()) {
        1 -> serializePropertyKeyV1(key)
        else -> throw NotImplementedError()
    }
}

fun deserializePropertyKey(key: String): PropertyKey {
    val parts = key.split(DELIMITER)
    if (parts.isEmpty()) {
        throw IllegalStateException("No version in key $key")
    }
    val version = try {
        parts[0].toInt()
    } catch (e: Exception) {
        throw IllegalStateException("Wrong version in key $key [${parts[0]}]", e)
    }

    return when(version) {
        1 -> deserializePropertyKeyV1(version, parts)
        else -> throw NotImplementedError()
    }
}

private fun serializePropertyKeyV1(key: PropertyKey): String {
    if (key.version.toInt() != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }
    return "${key.version}$DELIMITER" +
            "${key.namespace}$DELIMITER" +
            "${key.service}$DELIMITER" +
            "${key.appId}$DELIMITER" +
            key.key
}

private fun deserializePropertyKeyV1(version: Int, parts: List<String>): PropertyKey {
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    if (parts.size != 5) {
        throw IllegalStateException("Key v1 should contain exactly 5 parts")
    }

    return PropertyKey(
        version = version.toByte(),
        namespace = parts[1],
        service = parts[2],
        appId = parts[3],
        key = parts[4],
    )
}