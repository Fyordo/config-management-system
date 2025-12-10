package com.fyordo.cms.server.service.raft

import com.fyordo.cms.server.dto.RaftCommand
import com.fyordo.cms.server.dto.RaftOp
import com.fyordo.cms.server.serialization.deserializeRaftCommand
import com.fyordo.cms.server.service.storage.InMemoryStorage
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
class RaftStateMachine : BaseStateMachine() {
    private val store = InMemoryStorage()

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
                    .let(Message::valueOf)
            }.getOrElse { e ->
                logger.error(e) { "Error processing query" }
                Message.valueOf("ERROR: ${e.message}")
            }
        }

    private fun processCommand(command: RaftCommand): String {
        return when (command.operation) {
            RaftOp.PUT -> {
                store[command.key] = command.value
                "OK"
            }

            RaftOp.GET -> {
                store[command.key]?.toString(Charsets.UTF_8) ?: "NULL"
            }

            RaftOp.DELETE -> {
                store.remove(command.key)?.let { "OK" } ?: "NOT_FOUND"
            }
        }
    }
}

