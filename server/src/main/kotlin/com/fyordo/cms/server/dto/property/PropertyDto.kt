package com.fyordo.cms.server.dto.property

import com.fyordo.cms.CmsProto
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class PropertyDto(
    val key: PropertyKeyDto,
    val value: PropertyValueDto,
) {
    constructor(entry: CmsProto.PropertyInternalDto) : this(
        key = PropertyKeyDto(entry.key),
        value = PropertyValueDto(entry.value)
    )
}

data class PropertyKeyDto @JsonCreator constructor(
    @param:JsonProperty("version") val version: Int,
    @param:JsonProperty("namespace") val namespace: String,
    @param:JsonProperty("service") val service: String,
    @param:JsonProperty("appId") val appId: String,
    @param:JsonProperty("key") val key: String
) {
    constructor(propertyKey: CmsProto.PropertyKey) : this(
        version = propertyKey.version,
        namespace = propertyKey.namespace,
        service = propertyKey.service,
        appId = propertyKey.appId,
        key = propertyKey.key
    )

    fun toProto(): CmsProto.PropertyKey =
        CmsProto.PropertyKey.newBuilder()
            .setVersion(version)
            .setNamespace(namespace)
            .setService(service)
            .setAppId(appId)
            .setKey(key)
            .build()
}

data class PropertyValueDto @JsonCreator constructor(
    @param:JsonProperty("value") val value: String,
    @param:JsonProperty("lastModifiedMs") val lastModifiedMs: Long
) {
    constructor(propertyValue: CmsProto.PropertyValue) : this(
        value = String(propertyValue.value.toByteArray(), Charsets.UTF_8),
        lastModifiedMs = propertyValue.lastModifiedMs
    )
}