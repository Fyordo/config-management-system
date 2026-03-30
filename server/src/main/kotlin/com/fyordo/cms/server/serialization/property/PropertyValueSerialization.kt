package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.property.PropertyValue
import com.google.protobuf.ByteString

fun serializePropertyValue(propertyValue: PropertyValue): ByteArray {
    return toPropertyValueProto(propertyValue).toByteArray()
}

fun deserializePropertyValue(propertyValue: ByteArray): PropertyValue {
    return fromPropertyValueProto(CmsProto.PropertyValueProto.parseFrom(propertyValue))
}

fun serializePropertyValueV1(propertyValue: PropertyValue): ByteArray {
    val version = propertyValue.version.toInt()
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    return toPropertyValueProto(propertyValue).toByteArray()
}

fun deserializePropertyValueV1(valueProto: CmsProto.PropertyValueProto): PropertyValue {
    return fromPropertyValueProto(valueProto)
}

fun toPropertyValueProto(propertyValue: PropertyValue): CmsProto.PropertyValueProto {
    val version = propertyValue.version.toInt()
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    return CmsProto.PropertyValueProto.newBuilder()
        .setVersion(version)
        .setLastModifiedMs(propertyValue.lastModifiedMs)
        .setValue(ByteString.copyFrom(propertyValue.value))
        .build()
}

fun fromPropertyValueProto(valueProto: CmsProto.PropertyValueProto): PropertyValue {
    val version = valueProto.version
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    return PropertyValue(
        value = valueProto.value.toByteArray(),
        lastModifiedMs = valueProto.lastModifiedMs,
        version = version.toByte()
    )
}