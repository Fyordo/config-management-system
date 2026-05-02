package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.CmsDtos

fun serializePropertyKey(propertyKey: CmsDtos.PropertyKey): ByteArray {
    return propertyKey.toByteArray()
}

fun deserializePropertyKey(propertyKey: ByteArray): CmsDtos.PropertyKey {
    return CmsDtos.PropertyKey.parseFrom(propertyKey)
}