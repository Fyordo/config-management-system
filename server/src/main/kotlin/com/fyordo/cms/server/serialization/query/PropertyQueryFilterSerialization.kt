package com.fyordo.cms.server.serialization.query

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.query.PropertyQueryFilter

fun serializePropertyQueryFilter(filter: PropertyQueryFilter): ByteArray {
    return toPropertyQueryFilterProto(filter).toByteArray()
}

fun deserializePropertyQueryFilter(filter: ByteArray): PropertyQueryFilter {
    return fromPropertyQueryFilterProto(CmsProto.PropertyQueryFilterProto.parseFrom(filter))
}

fun toPropertyQueryFilterProto(filter: PropertyQueryFilter): CmsProto.PropertyQueryFilterProto {
    val builder = CmsProto.PropertyQueryFilterProto.newBuilder()
        .setLimit(filter.limit)
    filter.namespaceRegex?.let(builder::setNamespaceRegex)
    filter.serviceRegex?.let(builder::setServiceRegex)
    filter.appIdRegex?.let(builder::setAppIdRegex)
    filter.keyRegex?.let(builder::setKeyRegex)
    filter.valueRegex?.let(builder::setValueRegex)
    return builder.build()
}

fun fromPropertyQueryFilterProto(proto: CmsProto.PropertyQueryFilterProto): PropertyQueryFilter {
    val namespace = if (proto.hasNamespaceRegex()) proto.namespaceRegex else null
    val service = if (proto.hasServiceRegex()) proto.serviceRegex else null
    val appId = if (proto.hasAppIdRegex()) proto.appIdRegex else null
    val key = if (proto.hasKeyRegex()) proto.keyRegex else null
    val value = if (proto.hasValueRegex()) proto.valueRegex else null
    val limit = proto.limit

    return PropertyQueryFilter(
        namespace,
        service,
        appId,
        key,
        value,
        limit
    )
}