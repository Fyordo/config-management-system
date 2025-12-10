package com.fyordo.cms.server.serialization.raft

import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.raft.RaftCommand
import com.fyordo.cms.server.dto.raft.RaftOp
import com.fyordo.cms.server.serialization.property.deserializePropertyKey
import com.fyordo.cms.server.serialization.property.serializePropertyKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.io.encoding.Base64

fun serializeRaftCommand(command: RaftCommand): String {
    return when (command.version.toInt()) {
        1 -> serializeRaftCommandV1(command)
        else -> throw NotImplementedError()
    }
}

fun deserializeRaftCommand(command: String): RaftCommand {
    val bytes = Base64.decode(command)

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

    if (command.key == null) {
        dataStream.writeInt(0)
    } else {
        val keyBytes = serializePropertyKey(command.key)
        dataStream.writeInt(keyBytes.size)
        dataStream.write(keyBytes)
    }

    val valueBytes = command.value
    dataStream.writeInt(valueBytes.size)
    dataStream.write(valueBytes)

    dataStream.flush()

    return Base64.encode(byteStream.toByteArray())
}

fun deserializeRaftCommandV1(version: Int, dataStream: DataInputStream): RaftCommand {
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    val operationByte = dataStream.readByte()
    val operation = RaftOp.getByValue(operationByte)

    val keyLength = dataStream.readInt()
    var key: PropertyKey? = null
    if (keyLength != 0) {
        val keyBytes = ByteArray(keyLength)
        dataStream.readFully(keyBytes)
        key = deserializePropertyKey(keyBytes)
    }

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