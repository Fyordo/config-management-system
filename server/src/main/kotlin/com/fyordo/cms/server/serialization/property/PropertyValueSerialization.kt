package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.CmsDtos

fun serializePropertyValue(propertyValue: CmsDtos.PropertyValue): ByteArray {
    return propertyValue.toByteArray()
}

fun deserializePropertyValue(propertyValue: ByteArray): CmsDtos.PropertyValue {
    return CmsDtos.PropertyValue.parseFrom(propertyValue)
}