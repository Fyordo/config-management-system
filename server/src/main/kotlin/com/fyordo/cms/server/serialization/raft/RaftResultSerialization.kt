package com.fyordo.cms.server.serialization.raft

import com.fyordo.cms.CmsProto
import com.google.protobuf.ByteString

fun serializeRaftResult(result: CmsProto.RaftResultProto): ByteArray {
    return result.toByteArray()
}

fun deserializeRaftResult(result: ByteArray): CmsProto.RaftResultProto {
    return CmsProto.RaftResultProto.parseFrom(result)
}

fun raftOkResult(payload: ByteArray = byteArrayOf()): CmsProto.RaftResultProto {
    return CmsProto.RaftResultProto.newBuilder()
        .setVersion(1)
        .setStatus(CmsProto.RaftResultStatusProto.RAFT_RESULT_STATUS_OK)
        .setResult(ByteString.copyFrom(payload))
        .build()
}

fun raftNotFoundResult(): CmsProto.RaftResultProto {
    return CmsProto.RaftResultProto.newBuilder()
        .setVersion(1)
        .setStatus(CmsProto.RaftResultStatusProto.RAFT_RESULT_STATUS_NOT_FOUND)
        .setResult(ByteString.EMPTY)
        .build()
}

fun raftErrorResult(): CmsProto.RaftResultProto {
    return CmsProto.RaftResultProto.newBuilder()
        .setVersion(1)
        .setStatus(CmsProto.RaftResultStatusProto.RAFT_RESULT_STATUS_ERROR)
        .setResult(ByteString.EMPTY)
        .build()
}