package com.fyordo.cms.server.dto.property

import com.fyordo.cms.CmsProto

data class PropertyUpdateEvent(
    val key: CmsProto.PropertyKey,
    val value: CmsProto.PropertyValue? = null,
    val revision: Long = 0L
)