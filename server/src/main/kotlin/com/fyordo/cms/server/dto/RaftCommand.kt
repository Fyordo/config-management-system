package com.fyordo.cms.server.dto

data class RaftCommand(
    val operation: RaftOp,
    val key: String,
    val value: String,
    override val version: Byte = 1
) : Versioned

enum class RaftOp(val value: Byte) {
    GET(1), PUT(2), DELETE(3)
}
