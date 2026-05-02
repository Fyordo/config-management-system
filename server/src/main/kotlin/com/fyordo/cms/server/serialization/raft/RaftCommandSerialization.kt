package com.fyordo.cms.server.serialization.raft

import com.fyordo.cms.CmsDtos
import com.google.protobuf.ByteString

fun serializeRaftCommand(command: CmsDtos.RaftCommand): ByteArray {
    return command.toByteArray()
}

fun deserializeRaftCommand(command: ByteArray): CmsDtos.RaftCommand {
    return CmsDtos.RaftCommand.parseFrom(command)
}

fun raftPutCommand(key: CmsDtos.PropertyKey, valuePayload: ByteArray): CmsDtos.RaftCommand =
    CmsDtos.RaftCommand.newBuilder()
        .setVersion(1)
        .setOperation(CmsDtos.RaftOp.RAFT_OP_PUT)
        .setKey(key)
        .setValue(ByteString.copyFrom(valuePayload))
        .build()

fun raftDeleteCommand(key: CmsDtos.PropertyKey): CmsDtos.RaftCommand =
    CmsDtos.RaftCommand.newBuilder()
        .setVersion(1)
        .setOperation(CmsDtos.RaftOp.RAFT_OP_DELETE)
        .setKey(key)
        .setValue(ByteString.EMPTY)
        .build()
