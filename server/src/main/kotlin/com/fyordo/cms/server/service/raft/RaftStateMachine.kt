package com.fyordo.cms.server.service.raft

import com.fyordo.cms.server.dto.raft.RaftCommand
import com.fyordo.cms.server.dto.raft.RaftOp
import com.fyordo.cms.server.dto.raft.RaftResult
import com.fyordo.cms.server.dto.raft.RaftResultStatus
import com.fyordo.cms.server.serialization.property.deserializePropertyValue
import com.fyordo.cms.server.serialization.property.serializePropertyInternalDto
import com.fyordo.cms.server.serialization.property.serializePropertyValue
import com.fyordo.cms.server.serialization.query.deserializePropertyQueryFilter
import com.fyordo.cms.server.serialization.raft.deserializeRaftCommand
import com.fyordo.cms.server.serialization.raft.serializeRaftResult
import com.fyordo.cms.server.serialization.serializeList
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import com.fyordo.cms.server.utils.EMPTY_BYTES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import mu.KotlinLogging
import org.apache.ratis.protocol.Message
import org.apache.ratis.protocol.RaftGroupId
import org.apache.ratis.server.RaftServer
import org.apache.ratis.server.storage.RaftStorage
import org.apache.ratis.statemachine.TransactionContext
import org.apache.ratis.statemachine.impl.BaseStateMachine
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

@Component
class RaftStateMachine(
    private val store: PropertyInMemoryStorage
) : BaseStateMachine() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun initialize(
        server: RaftServer,
        groupId: RaftGroupId,
        storage: RaftStorage
    ) {
        super.initialize(server, groupId, storage)
        logger.info { "RaftStateMachine initialized for group: ${groupId.uuid}" }
    }

    override fun close() {
        try {
            scope.cancel("RaftStateMachine is closing")
            logger.info { "CoroutineScope cancelled" }
        } catch (e: Exception) {
            logger.warn(e) { "Error cancelling scope" }
        } finally {
            super.close()
            logger.info { "RaftStateMachine closed" }
        }
    }

    override fun applyTransaction(trx: TransactionContext): CompletableFuture<Message> =
        scope.future {
            runCatching {
                trx.logEntry
                    .stateMachineLogEntry
                    .logData
                    .toStringUtf8()
                    .let(::deserializeRaftCommand)
                    .also { logger.debug { "Applying: ${it.operation} ${it.key}" } }
                    .let(::processCommand)
                    .let(::serializeRaftResult)
                    .let(Message::valueOf)
            }.getOrElse { e ->
                logger.warn(e) { "Error applying transaction" }
                Message.valueOf("ERROR: ${e.message}")
            }
        }

    override fun query(request: Message): CompletableFuture<Message> =
        scope.future {
            runCatching {
                request.content
                    .toStringUtf8()
                    .let(::deserializeRaftCommand)
                    .also { logger.info { "Query: ${it.operation} ${it.key}" } }
                    .let(::processCommand)
                    .let(::serializeRaftResult)
                    .let(Message::valueOf)
            }.getOrElse { e ->
                logger.error(e) { "Error processing query" }
                Message.valueOf("ERROR: ${e.message}")
            }
        }

    private fun processCommand(command: RaftCommand): RaftResult {
        return when (command.operation) {
            RaftOp.PUT -> {
                store[command.key!!] = deserializePropertyValue(command.value)
                RaftResult(
                    result = EMPTY_BYTES,
                    status = RaftResultStatus.OK
                )
            }

            RaftOp.GET -> {
                store[command.key!!]?.let {
                    RaftResult(
                        result = serializePropertyValue(it),
                        status = RaftResultStatus.OK
                    )
                } ?: RaftResult(
                    result = EMPTY_BYTES,
                    status = RaftResultStatus.NOT_FOUND
                )
            }

            RaftOp.DELETE -> {
                store.remove(command.key!!)?.let {
                    RaftResult(
                        result = EMPTY_BYTES,
                        status = RaftResultStatus.OK
                    )
                } ?: RaftResult(
                    result = EMPTY_BYTES,
                    status = RaftResultStatus.NOT_FOUND
                )
            }

            RaftOp.QUERY -> {
                val filter = deserializePropertyQueryFilter(command.value)
                RaftResult(
                    result = serializeList(
                        store.getByFilter(filter).toList(), ::serializePropertyInternalDto
                    ),
                    status = RaftResultStatus.OK
                )
            }
        }
    }
}

