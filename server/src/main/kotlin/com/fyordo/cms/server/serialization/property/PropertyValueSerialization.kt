package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.server.dto.property.PropertyValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

fun serializePropertyValue(propertyValue: PropertyValue): ByteArray {
    return when (propertyValue.version.toInt()) {
        1 -> serializePropertyValueV1(propertyValue)
        else -> throw NotImplementedError()
    }
}

fun deserializePropertyValue(propertyValue: ByteArray): PropertyValue {
    val byteStream = ByteArrayInputStream(propertyValue)
    val dataStream = DataInputStream(byteStream)

    val version = dataStream.readByte().toInt()

    return when (version) {
        1 -> deserializePropertyValueV1(version, dataStream)
        else -> throw NotImplementedError()
    }
}

fun serializePropertyValueV1(propertyValue: PropertyValue): ByteArray {
    val version = propertyValue.version.toInt()
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    val byteStream = ByteArrayOutputStream()
    val dataStream = DataOutputStream(byteStream)

    dataStream.writeByte(version)
    dataStream.writeLong(propertyValue.lastModifiedMs)

    val valueBytes = propertyValue.value
    dataStream.writeInt(valueBytes.size)
    dataStream.write(valueBytes)

    dataStream.flush()

    return byteStream.toByteArray()
}

fun deserializePropertyValueV1(version: Int, dataStream: DataInputStream): PropertyValue {
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    val lastModifiedMs = dataStream.readLong()

    val valueLength = dataStream.readInt()
    val valueBytes = ByteArray(valueLength)
    dataStream.readFully(valueBytes)

    return PropertyValue(
        value = valueBytes,
        lastModifiedMs = lastModifiedMs,
        version = version.toByte()
    )
}