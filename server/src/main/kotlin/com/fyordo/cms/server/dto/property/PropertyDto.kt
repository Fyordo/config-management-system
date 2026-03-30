package com.fyordo.cms.server.dto.property

data class PropertyDto(
    val key: PropertyKeyDto,
    val value: PropertyValueDto,
) {
    constructor(entry: PropertyEntry) : this(
        key = PropertyKeyDto(entry.first),
        value = PropertyValueDto(entry.second)
    )
}

data class PropertyKeyDto(
    val namespace: String,
    val service: String,
    val appId: String,
    val key: String
) {
    constructor(propertyKey: PropertyKey) : this(
        namespace = propertyKey.namespace,
        service = propertyKey.service,
        appId = propertyKey.appId,
        key = propertyKey.key
    )
}

data class PropertyValueDto(
    val value: String,
    val lastModifiedMs: Long
) {
    constructor(propertyValue: PropertyValue) : this(
        value = String(propertyValue.value, Charsets.UTF_8),
        lastModifiedMs = propertyValue.lastModifiedMs
    )
}