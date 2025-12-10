package com.fyordo.cms.server.serialization.query

import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

fun serializePropertyQueryFilter(filter: PropertyQueryFilter): ByteArray {
    val byteStream = ByteArrayOutputStream()
    val dataStream = DataOutputStream(byteStream)

    serializeNullableString(filter.namespaceRegex, dataStream)
    serializeNullableString(filter.serviceRegex, dataStream)
    serializeNullableString(filter.appIdRegex, dataStream)
    serializeNullableString(filter.keyRegex, dataStream)
    serializeNullableString(filter.valueRegex, dataStream)

    dataStream.writeInt(filter.limit)

    dataStream.flush()

    return byteStream.toByteArray()
}

private fun serializeNullableString(value: String?, ds: DataOutputStream) {
    if (value == null) {
        ds.writeInt(0)
        return
    }
    val valueBytes = value.toByteArray(Charsets.UTF_8)
    ds.writeInt(valueBytes.size)
    ds.write(valueBytes)
}

fun deserializePropertyQueryFilter(filter: ByteArray): PropertyQueryFilter {
    val byteStream = ByteArrayInputStream(filter)
    val dataStream = DataInputStream(byteStream)

    val namespace = deserializeNullableString(dataStream)
    val service = deserializeNullableString(dataStream)
    val appId = deserializeNullableString(dataStream)
    val key = deserializeNullableString(dataStream)
    val value = deserializeNullableString(dataStream)
    val limit = dataStream.readInt()

    return PropertyQueryFilter(
        namespace,
        service,
        appId,
        key,
        value,
        limit
    )
}

private fun deserializeNullableString(ds: DataInputStream): String? {
    val length = ds.readInt()
    if (length == 0) {
        return null
    }
    val valueBytes = ByteArray(length)
    ds.readFully(valueBytes)

    return String(valueBytes, Charsets.UTF_8)
}