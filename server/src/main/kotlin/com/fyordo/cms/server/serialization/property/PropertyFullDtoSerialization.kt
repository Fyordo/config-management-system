package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.CmsProto

fun serializePropertyInternalDto(entry: CmsProto.PropertyInternalDto): ByteArray {
    return entry.toByteArray()
}

fun deserializePropertyInternalDto(dtoBytes: ByteArray): CmsProto.PropertyInternalDto {
    return CmsProto.PropertyInternalDto.parseFrom(dtoBytes)
}

fun serializePropertyInternalDtoList(entries: List<CmsProto.PropertyInternalDto>): ByteArray {
    return CmsProto.PropertyInternalList.newBuilder()
        .addAllItems(entries)
        .build()
        .toByteArray()
}

fun deserializePropertyInternalDtoList(bytes: ByteArray): List<CmsProto.PropertyInternalDto> {
    return CmsProto.PropertyInternalList.parseFrom(bytes)
        .itemsList
}