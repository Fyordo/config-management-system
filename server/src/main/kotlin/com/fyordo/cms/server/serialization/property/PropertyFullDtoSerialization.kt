package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.property.PropertyEntry

fun serializePropertyInternalDto(entry: PropertyEntry): ByteArray {
    return toPropertyInternalDtoProto(entry).toByteArray()
}

fun deserializePropertyInternalDto(dtoBytes: ByteArray): PropertyEntry {
    return fromPropertyInternalDtoProto(CmsProto.PropertyInternalDto.parseFrom(dtoBytes))
}

fun toPropertyInternalDtoProto(entry: PropertyEntry): CmsProto.PropertyInternalDto {
    return CmsProto.PropertyInternalDto.newBuilder()
        .setKey(toPropertyKeyProto(entry.first))
        .setValue(toPropertyValueProto(entry.second))
        .build()
}

fun fromPropertyInternalDtoProto(dtoProto: CmsProto.PropertyInternalDto): PropertyEntry {
    return fromPropertyKeyProto(dtoProto.key) to fromPropertyValueProto(dtoProto.value)
}

fun serializePropertyInternalDtoList(entries: List<PropertyEntry>): ByteArray {
    return CmsProto.PropertyInternalList.newBuilder()
        .addAllItems(entries.map(::toPropertyInternalDtoProto))
        .build()
        .toByteArray()
}

fun deserializePropertyInternalDtoList(bytes: ByteArray): List<PropertyEntry> {
    return CmsProto.PropertyInternalList.parseFrom(bytes)
        .itemsList
        .map(::fromPropertyInternalDtoProto)
}