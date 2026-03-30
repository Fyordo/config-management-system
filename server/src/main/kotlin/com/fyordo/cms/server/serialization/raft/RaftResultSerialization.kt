package com.fyordo.cms.server.serialization.raft

import com.fyordo.cms.CmsProto
import com.google.protobuf.ByteString

fun serializeRaftResult(result: CmsProto.RaftResult): ByteArray {
    return result.toByteArray()
}

fun deserializeRaftResult(result: ByteArray): CmsProto.RaftResult {
    return CmsProto.RaftResult.parseFrom(result)
}

fun raftOkResult(payload: ByteArray = byteArrayOf()): CmsProto.RaftResult {
    return CmsProto.RaftResult.newBuilder()
        .setVersion(1)
        .setStatus(CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_OK)
        .setResult(ByteString.copyFrom(payload))
        .build()
}

fun raftNotFoundResult(): CmsProto.RaftResult {
    return CmsProto.RaftResult.newBuilder()
        .setVersion(1)
        .setStatus(CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_NOT_FOUND)
        .setResult(ByteString.EMPTY)
        .build()
}

fun raftErrorResult(): CmsProto.RaftResult {
    return CmsProto.RaftResult.newBuilder()
        .setVersion(1)
        .setStatus(CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_ERROR)
        .setResult(ByteString.EMPTY)
        .build()
}