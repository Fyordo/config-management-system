package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.server.dto.property.PropertyKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

fun serializePropertyKey(propertyKey: PropertyKey): ByteArray {
    return when (propertyKey.version.toInt()) {
        1 -> serializePropertyKeyV1(propertyKey)
        else -> throw NotImplementedError()
    }
}

fun deserializePropertyKey(propertyKey: ByteArray): PropertyKey {
    val byteStream = ByteArrayInputStream(propertyKey)
    val dataStream = DataInputStream(byteStream)

    val version = dataStream.readByte().toInt()

    return when (version) {
        1 -> deserializePropertyKeyV1(version, dataStream)
        else -> throw NotImplementedError()
    }
}

fun serializePropertyKeyV1(propertyKey: PropertyKey): ByteArray {
    val version = propertyKey.version.toInt()
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    val byteStream = ByteArrayOutputStream()
    val dataStream = DataOutputStream(byteStream)

    dataStream.writeByte(version)
    
    // Сериализация строковых полей
    writeString(dataStream, propertyKey.namespace)
    writeString(dataStream, propertyKey.service)
    writeString(dataStream, propertyKey.appId)
    writeString(dataStream, propertyKey.key)

    dataStream.flush()

    return byteStream.toByteArray()
}

fun deserializePropertyKeyV1(version: Int, dataStream: DataInputStream): PropertyKey {
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    val namespace = readString(dataStream)
    val service = readString(dataStream)
    val appId = readString(dataStream)
    val key = readString(dataStream)

    return PropertyKey(
        version = version.toByte(),
        namespace = namespace,
        service = service,
        appId = appId,
        key = key
    )
}

private fun writeString(dataStream: DataOutputStream, value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    dataStream.writeInt(bytes.size)
    dataStream.write(bytes)
}

private fun readString(dataStream: DataInputStream): String {
    val length = dataStream.readInt()
    val bytes = ByteArray(length)
    dataStream.readFully(bytes)
    return String(bytes, Charsets.UTF_8)
}