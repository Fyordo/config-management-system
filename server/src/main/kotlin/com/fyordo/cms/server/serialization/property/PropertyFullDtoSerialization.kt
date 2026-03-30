package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.property.PropertyEntry

fun serializePropertyInternalDto(entry: PropertyEntry): ByteArray {
    return toPropertyInternalDtoProto(entry).toByteArray()
}

fun deserializePropertyInternalDto(dtoBytes: ByteArray): PropertyEntry {
    return fromPropertyInternalDtoProto(CmsProto.PropertyInternalDtoProto.parseFrom(dtoBytes))
}

fun toPropertyInternalDtoProto(entry: PropertyEntry): CmsProto.PropertyInternalDtoProto {
    return CmsProto.PropertyInternalDtoProto.newBuilder()
        .setKey(toPropertyKeyProto(entry.first))
        .setValue(toPropertyValueProto(entry.second))
        .build()
}

fun fromPropertyInternalDtoProto(dtoProto: CmsProto.PropertyInternalDtoProto): PropertyEntry {
    return fromPropertyKeyProto(dtoProto.key) to fromPropertyValueProto(dtoProto.value)
}

fun serializePropertyInternalDtoList(entries: List<PropertyEntry>): ByteArray {
    return CmsProto.PropertyInternalListProto.newBuilder()
        .addAllItems(entries.map(::toPropertyInternalDtoProto))
        .build()
        .toByteArray()
}

fun deserializePropertyInternalDtoList(bytes: ByteArray): List<PropertyEntry> {
    return CmsProto.PropertyInternalListProto.parseFrom(bytes)
        .itemsList
        .map(::fromPropertyInternalDtoProto)
}