package com.fyordo.cms.server.serialization.raft

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import com.fyordo.cms.server.serialization.property.fromPropertyKeyProto
import com.fyordo.cms.server.serialization.property.toPropertyKeyProto
import com.fyordo.cms.server.serialization.query.fromPropertyQueryFilterProto
import com.fyordo.cms.server.serialization.query.toPropertyQueryFilterProto
import com.google.protobuf.ByteString

fun serializeRaftCommand(command: CmsProto.RaftCommand): ByteArray {
    return command.toByteArray()
}

fun deserializeRaftCommand(command: ByteArray): CmsProto.RaftCommand {
    return CmsProto.RaftCommand.parseFrom(command)
}

fun raftGetCommand(key: PropertyKey): CmsProto.RaftCommand =
    CmsProto.RaftCommand.newBuilder()
        .setVersion(1)
        .setOperation(CmsProto.RaftOp.RAFT_OP_GET)
        .setKey(toPropertyKeyProto(key))
        .setValue(ByteString.EMPTY)
        .build()

fun raftPutCommand(key: PropertyKey, valuePayload: ByteArray): CmsProto.RaftCommand =
    CmsProto.RaftCommand.newBuilder()
        .setVersion(1)
        .setOperation(CmsProto.RaftOp.RAFT_OP_PUT)
        .setKey(toPropertyKeyProto(key))
        .setValue(ByteString.copyFrom(valuePayload))
        .build()

fun raftDeleteCommand(key: PropertyKey): CmsProto.RaftCommand =
    CmsProto.RaftCommand.newBuilder()
        .setVersion(1)
        .setOperation(CmsProto.RaftOp.RAFT_OP_DELETE)
        .setKey(toPropertyKeyProto(key))
        .setValue(ByteString.EMPTY)
        .build()

fun raftQueryCommand(filter: PropertyQueryFilter): CmsProto.RaftCommand =
    CmsProto.RaftCommand.newBuilder()
        .setVersion(1)
        .setOperation(CmsProto.RaftOp.RAFT_OP_QUERY)
        .setValue(toPropertyQueryFilterProto(filter).toByteString())
        .build()

fun raftCommandKey(command: CmsProto.RaftCommand): PropertyKey? {
    return if (command.hasKey()) fromPropertyKeyProto(command.key) else null
}