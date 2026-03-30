package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.property.PropertyKey

fun serializePropertyKey(propertyKey: PropertyKey): ByteArray {
    return toPropertyKeyProto(propertyKey).toByteArray()
}

fun deserializePropertyKey(propertyKey: ByteArray): PropertyKey {
    return fromPropertyKeyProto(CmsProto.PropertyKeyProto.parseFrom(propertyKey))
}

fun serializePropertyKeyV1(propertyKey: PropertyKey): ByteArray {
    val version = propertyKey.version.toInt()
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    return toPropertyKeyProto(propertyKey).toByteArray()
}

fun deserializePropertyKeyV1(keyProto: CmsProto.PropertyKeyProto): PropertyKey {
    return fromPropertyKeyProto(keyProto)
}

fun toPropertyKeyProto(propertyKey: PropertyKey): CmsProto.PropertyKeyProto {
    val version = propertyKey.version.toInt()
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    return CmsProto.PropertyKeyProto.newBuilder()
        .setVersion(propertyKey.version.toInt())
        .setNamespace(propertyKey.namespace)
        .setService(propertyKey.service)
        .setAppId(propertyKey.appId)
        .setKey(propertyKey.key)
        .build()
}

fun fromPropertyKeyProto(keyProto: CmsProto.PropertyKeyProto): PropertyKey {
    val version = keyProto.version
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    return PropertyKey(
        version = version.toByte(),
        namespace = keyProto.namespace,
        service = keyProto.service,
        appId = keyProto.appId,
        key = keyProto.key
    )
}