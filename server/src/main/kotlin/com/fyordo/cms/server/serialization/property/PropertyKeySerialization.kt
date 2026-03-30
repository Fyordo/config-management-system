package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.CmsProto

fun serializePropertyKey(propertyKey: CmsProto.PropertyKey): ByteArray {
    return propertyKey.toByteArray()
}

fun deserializePropertyKey(propertyKey: ByteArray): CmsProto.PropertyKey {
    return CmsProto.PropertyKey.parseFrom(propertyKey)
}