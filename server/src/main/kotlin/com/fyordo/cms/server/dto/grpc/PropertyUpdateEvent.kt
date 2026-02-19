package com.fyordo.cms.server.dto.grpc

import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue

data class PropertyUpdateEvent(
    val key: PropertyKey,
    val value: PropertyValue? = null
)
