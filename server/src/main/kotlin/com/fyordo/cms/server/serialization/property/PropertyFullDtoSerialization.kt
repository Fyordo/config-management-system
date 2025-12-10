package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.server.dto.property.PropertyInternalDto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

fun serializePropertyInternalDto(dto: PropertyInternalDto): ByteArray {
    val byteStream = ByteArrayOutputStream()
    val dataStream = DataOutputStream(byteStream)

    val keyBytes = serializePropertyKey(dto.key)
    dataStream.writeInt(keyBytes.size)
    dataStream.write(keyBytes)
    val valueBytes = serializePropertyValue(dto.value)
    dataStream.writeInt(valueBytes.size)
    dataStream.write(valueBytes)

    dataStream.flush()
    return byteStream.toByteArray()
}

fun deserializePropertyInternalDto(dtoBytes: ByteArray): PropertyInternalDto {
    val byteStream = ByteArrayInputStream(dtoBytes)
    val dataStream = DataInputStream(byteStream)

    val keySize = dataStream.readInt()
    val keyBytes = ByteArray(keySize)
    dataStream.readFully(keyBytes)
    val key = deserializePropertyKey(keyBytes)

    val valueSize = dataStream.readInt()
    val valueBytes = ByteArray(valueSize)
    dataStream.readFully(valueBytes)
    val value = deserializePropertyValue(valueBytes)

    return PropertyInternalDto(key, value)
}