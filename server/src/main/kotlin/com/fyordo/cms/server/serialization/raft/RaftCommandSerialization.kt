package com.fyordo.cms.server.serialization.raft

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import com.fyordo.cms.server.serialization.query.toPropertyQueryFilterProto
import com.google.protobuf.ByteString

fun serializeRaftCommand(command: CmsProto.RaftCommand): ByteArray {
    return command.toByteArray()
}

fun deserializeRaftCommand(command: ByteArray): CmsProto.RaftCommand {
    return CmsProto.RaftCommand.parseFrom(command)
}

fun raftGetCommand(key: CmsProto.PropertyKey): CmsProto.RaftCommand =
    CmsProto.RaftCommand.newBuilder()
        .setVersion(1)
        .setOperation(CmsProto.RaftOp.RAFT_OP_GET)
        .setKey(key)
        .setValue(ByteString.EMPTY)
        .build()

fun raftPutCommand(key: CmsProto.PropertyKey, valuePayload: ByteArray): CmsProto.RaftCommand =
    CmsProto.RaftCommand.newBuilder()
        .setVersion(1)
        .setOperation(CmsProto.RaftOp.RAFT_OP_PUT)
        .setKey(key)
        .setValue(ByteString.copyFrom(valuePayload))
        .build()

fun raftDeleteCommand(key: CmsProto.PropertyKey): CmsProto.RaftCommand =
    CmsProto.RaftCommand.newBuilder()
        .setVersion(1)
        .setOperation(CmsProto.RaftOp.RAFT_OP_DELETE)
        .setKey(key)
        .setValue(ByteString.EMPTY)
        .build()

fun raftQueryCommand(filter: PropertyQueryFilter): CmsProto.RaftCommand =
    CmsProto.RaftCommand.newBuilder()
        .setVersion(1)
        .setOperation(CmsProto.RaftOp.RAFT_OP_QUERY)
        .setValue(toPropertyQueryFilterProto(filter).toByteString())
        .build()
