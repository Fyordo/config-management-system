package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.CmsProto

fun serializePropertyValue(propertyValue: CmsProto.PropertyValue): ByteArray {
    return propertyValue.toByteArray()
}

fun deserializePropertyValue(propertyValue: ByteArray): CmsProto.PropertyValue {
    return CmsProto.PropertyValue.parseFrom(propertyValue)
}