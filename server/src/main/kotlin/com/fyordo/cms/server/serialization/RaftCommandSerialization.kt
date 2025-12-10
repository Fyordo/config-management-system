package com.fyordo.cms.server.serialization

import com.fyordo.cms.server.dto.RaftCommand
import com.fyordo.cms.server.dto.RaftOp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.*

fun serializeRaftCommand(command: RaftCommand): String {
    return when (command.version.toInt()) {
        1 -> serializeRaftCommandV1(command)
        else -> throw NotImplementedError()
    }
}

fun deserializeRaftCommand(command: String): RaftCommand {
    val bytes = Base64.getDecoder().decode(command)

    val byteStream = ByteArrayInputStream(bytes)
    val dataStream = DataInputStream(byteStream)

    val version = dataStream.readByte().toInt()

    return when (version) {
        1 -> deserializeRaftCommandV1(version, dataStream)
        else -> throw NotImplementedError()
    }
}

fun serializeRaftCommandV1(command: RaftCommand): String {
    val version = command.version.toInt()
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    val byteStream = ByteArrayOutputStream()
    val dataStream = DataOutputStream(byteStream)

    dataStream.writeByte(version)

    dataStream.writeByte(command.operation.value.toInt())

    val keyBytes = serializePropertyKey(command.key).toByteArray(Charsets.UTF_8)
    dataStream.writeInt(keyBytes.size)
    dataStream.write(keyBytes)

    val valueBytes = command.value
    dataStream.writeInt(valueBytes.size)
    dataStream.write(valueBytes)

    dataStream.flush()

    return Base64.getEncoder().encodeToString(byteStream.toByteArray())
}

fun deserializeRaftCommandV1(version: Int, dataStream: DataInputStream): RaftCommand {
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    val operationByte = dataStream.readByte()
    val operation = RaftOp.entries.first { it.value == operationByte }

    val keyLength = dataStream.readInt()
    val keyBytes = ByteArray(keyLength)
    dataStream.readFully(keyBytes)
    val key = deserializePropertyKey(String(keyBytes, Charsets.UTF_8))

    val valueLength = dataStream.readInt()
    val valueBytes = ByteArray(valueLength)
    dataStream.readFully(valueBytes)

    return RaftCommand(
        operation = operation,
        key = key,
        value = valueBytes,
        version = version.toByte()
    )
}