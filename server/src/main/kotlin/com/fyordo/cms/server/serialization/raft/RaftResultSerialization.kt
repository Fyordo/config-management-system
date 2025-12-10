package com.fyordo.cms.server.serialization.raft

import com.fyordo.cms.server.dto.raft.RaftResult
import com.fyordo.cms.server.dto.raft.RaftResultStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.io.encoding.Base64

fun serializeRaftResult(result: RaftResult): String {
    return when (result.version.toInt()) {
        1 -> serializeRaftResultV1(result)
        else -> throw NotImplementedError()
    }
}

fun deserializeRaftResult(result: String): RaftResult {
    val bytes = Base64.decode(result)

    val byteStream = ByteArrayInputStream(bytes)
    val dataStream = DataInputStream(byteStream)

    val version = dataStream.readByte().toInt()

    return when (version) {
        1 -> deserializeRaftResultV1(version, dataStream)
        else -> throw NotImplementedError()
    }
}

fun serializeRaftResultV1(result: RaftResult): String {
    val version = result.version.toInt()
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    val byteStream = ByteArrayOutputStream()
    val dataStream = DataOutputStream(byteStream)

    dataStream.writeByte(version)

    dataStream.writeByte(result.status.value.toInt())

    dataStream.writeInt(result.result.size)
    dataStream.write(result.result)

    dataStream.flush()

    return Base64.encode(byteStream.toByteArray())
}

fun deserializeRaftResultV1(version: Int, dataStream: DataInputStream): RaftResult {
    if (version != 1) {
        throw IllegalStateException("Only version 1 is supported")
    }

    val statusByte = dataStream.readByte()
    val status = RaftResultStatus.getByValue(statusByte)

    val resultLength = dataStream.readInt()
    val resultBytes = ByteArray(resultLength)
    dataStream.readFully(resultBytes)

    return RaftResult(
        result = resultBytes,
        version = version.toByte(),
        status = status
    )
}