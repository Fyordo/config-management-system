package com.fyordo.cms.server.helper

import com.fyordo.cms.server.dto.RaftCommand
import com.fyordo.cms.server.dto.RaftOp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

fun serializeCommand(command: RaftCommand): String {
    val byteStream = ByteArrayOutputStream()
    val dataStream = DataOutputStream(byteStream)

    dataStream.writeByte(command.version.toInt())

    dataStream.writeByte(command.operation.value.toInt())

    val keyBytes = command.key.toByteArray(Charsets.UTF_8)
    dataStream.writeInt(keyBytes.size)
    dataStream.write(keyBytes)

    val valueBytes = command.value.toByteArray(Charsets.UTF_8)
    dataStream.writeInt(valueBytes.size)
    dataStream.write(valueBytes)
    
    dataStream.flush()

    return Base64.getEncoder().encodeToString(byteStream.toByteArray())
}

fun deserializeCommand(command: String): RaftCommand {
    val bytes = Base64.getDecoder().decode(command)
    
    val byteStream = ByteArrayInputStream(bytes)
    val dataStream = DataInputStream(byteStream)

    val version = dataStream.readByte()

    val operationByte = dataStream.readByte()
    val operation = RaftOp.entries.first { it.value == operationByte }

    val keyLength = dataStream.readInt()
    val keyBytes = ByteArray(keyLength)
    dataStream.readFully(keyBytes)
    val key = String(keyBytes, Charsets.UTF_8)
    
    val valueLength = dataStream.readInt()
    val valueBytes = ByteArray(valueLength)
    dataStream.readFully(valueBytes)
    val value = String(valueBytes, Charsets.UTF_8)
    
    return RaftCommand(
        operation = operation,
        key = key,
        value = value,
        version = version
    )
}