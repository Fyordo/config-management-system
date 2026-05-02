package com.fyordo.cms.server.serialization.raft

import com.fyordo.cms.CmsDtos
import com.google.protobuf.ByteString

fun serializeRaftResult(result: CmsDtos.RaftResult): ByteArray {
    return result.toByteArray()
}

fun deserializeRaftResult(result: ByteArray): CmsDtos.RaftResult {
    return CmsDtos.RaftResult.parseFrom(result)
}

fun raftOkResult(payload: ByteArray = byteArrayOf()): CmsDtos.RaftResult {
    return CmsDtos.RaftResult.newBuilder()
        .setVersion(1)
        .setStatus(CmsDtos.RaftResultStatus.RAFT_RESULT_STATUS_OK)
        .setResult(ByteString.copyFrom(payload))
        .build()
}

fun raftNotFoundResult(): CmsDtos.RaftResult {
    return CmsDtos.RaftResult.newBuilder()
        .setVersion(1)
        .setStatus(CmsDtos.RaftResultStatus.RAFT_RESULT_STATUS_NOT_FOUND)
        .setResult(ByteString.EMPTY)
        .build()
}

fun raftErrorResult(): CmsDtos.RaftResult {
    return CmsDtos.RaftResult.newBuilder()
        .setVersion(1)
        .setStatus(CmsDtos.RaftResultStatus.RAFT_RESULT_STATUS_ERROR)
        .setResult(ByteString.EMPTY)
        .build()
}