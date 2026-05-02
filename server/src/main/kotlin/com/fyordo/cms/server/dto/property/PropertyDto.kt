package com.fyordo.cms.server.dto.property

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fyordo.cms.CmsDtos

data class PropertyDto(
    val key: PropertyKeyDto,
    val value: PropertyValueDto,
) {
    constructor(entry: CmsDtos.PropertyInternalDto) : this(
        key = PropertyKeyDto(entry.key),
        value = PropertyValueDto(entry.value)
    )
}

data class PropertyKeyDto @JsonCreator constructor(
    @field:JsonProperty("version") val version: Int,
    @field:JsonProperty("namespace") val namespace: String,
    @field:JsonProperty("service") val service: String,
    @field:JsonProperty("appId") val appId: String,
    @field:JsonProperty("key") val key: String
) {
    constructor(propertyKey: CmsDtos.PropertyKey) : this(
        version = propertyKey.version,
        namespace = propertyKey.namespace,
        service = propertyKey.service,
        appId = propertyKey.appId,
        key = propertyKey.key
    )

    fun toProto(): CmsDtos.PropertyKey =
        CmsDtos.PropertyKey.newBuilder()
            .setVersion(version)
            .setNamespace(namespace)
            .setService(service)
            .setAppId(appId)
            .setKey(key)
            .build()
}

data class PropertyValueDto @JsonCreator constructor(
    @field:JsonProperty("value") val value: String,
    @field:JsonProperty("lastModifiedMs") val lastModifiedMs: Long
) {
    constructor(propertyValue: CmsDtos.PropertyValue) : this(
        value = String(propertyValue.value.toByteArray(), Charsets.UTF_8),
        lastModifiedMs = propertyValue.lastModifiedMs
    )
}