package com.fyordo.cms.server.serialization

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

fun <T> serializeList(list: List<T>, valueSerializer: (T) -> ByteArray): ByteArray {
    val byteStream = ByteArrayOutputStream()
    val dataStream = DataOutputStream(byteStream)

    dataStream.writeInt(list.size)
    list.forEach {
        val serializedValue = valueSerializer.invoke(it)
        dataStream.writeInt(serializedValue.size)
        dataStream.write(serializedValue)
    }

    dataStream.flush()
    return byteStream.toByteArray()
}

fun <T> deserializeList(value: ByteArray, valueDeserializer: (ByteArray) -> T): List<T> {
    val byteStream = ByteArrayInputStream(value)
    val dataStream = DataInputStream(byteStream)

    val length = dataStream.readInt()
    return buildList {
        repeat (length) {
            val size = dataStream.readInt()
            val elementBytes = ByteArray(size)
            dataStream.read(elementBytes)
            add(valueDeserializer(elementBytes))
        }
    }
}