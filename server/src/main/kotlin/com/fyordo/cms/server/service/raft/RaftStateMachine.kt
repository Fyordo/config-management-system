package com.fyordo.cms.server.service.raft

import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import com.fyordo.cms.server.dto.raft.RaftCommand
import com.fyordo.cms.server.dto.raft.RaftOp
import com.fyordo.cms.server.dto.raft.RaftResult
import com.fyordo.cms.server.dto.raft.RaftResultStatus
import com.fyordo.cms.server.service.PropertyUpdatePublisher
import com.fyordo.cms.server.serialization.property.deserializePropertyValue
import com.fyordo.cms.server.serialization.property.serializePropertyInternalDto
import com.fyordo.cms.server.serialization.property.serializePropertyValue
import com.fyordo.cms.server.serialization.query.deserializePropertyQueryFilter
import com.fyordo.cms.server.serialization.raft.deserializeRaftCommand
import com.fyordo.cms.server.serialization.raft.serializeRaftResult
import com.fyordo.cms.server.serialization.serializeList
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import com.fyordo.cms.server.utils.EMPTY_BYTES
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
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
    private val store: PropertyInMemoryStorage,
    private val propertyUpdatePublisher: PropertyUpdatePublisher
) : BaseStateMachine() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("RaftStateMachine"))

    override fun initialize(
        server: RaftServer,
        groupId: RaftGroupId,
        storage: RaftStorage
    ) {
        super.initialize(server, groupId, storage)
        logger.info { "RaftStateMachine initialized for group: ${groupId.uuid}" }
    }

    override fun close() {
        logger.info { "RaftStateMachine is closing" }
        try {
            runBlocking {
                scope.cancel("Cancelling CoroutineScope")
                // Wait a bit for active coroutines to finish
                delay(5000) // 5 seconds
            }
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
                val logData = trx.logEntry
                    .stateMachineLogEntry
                    .logData
                    .toStringUtf8()
                
                val command = try {
                    deserializeRaftCommand(logData)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to deserialize Raft command. Log data length: ${logData.length}" }
                    throw e
                }
                
                logger.debug { "Applying: ${command.operation} ${command.key}" }
                processCommand(command)
                    .let(::serializeRaftResult)
                    .let(Message::valueOf)
            }.getOrElse { e ->
                logger.error(e) { "Error applying transaction: ${e.javaClass.simpleName}: ${e.message}" }
                Message.valueOf(serializeRaftResult(
                    RaftResult(
                        result = EMPTY_BYTES,
                        status = RaftResultStatus.ERROR
                    )
                ))
            }
        }

    override fun query(request: Message): CompletableFuture<Message> =
        scope.future {
            runCatching {
                val requestContent = request.content.toStringUtf8()
                
                val command = try {
                    deserializeRaftCommand(requestContent)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to deserialize Raft query command. Content length: ${requestContent.length}" }
                    throw e
                }
                
                logger.debug { "Query: ${command.operation} ${command.key}" }
                processCommand(command)
                    .let(::serializeRaftResult)
                    .let(Message::valueOf)
            }.getOrElse { e ->
                logger.error(e) { "Error processing query: ${e.javaClass.simpleName}: ${e.message}" }
                Message.valueOf(serializeRaftResult(
                    RaftResult(
                        result = EMPTY_BYTES,
                        status = RaftResultStatus.ERROR
                    )
                ))
            }
        }

    private fun processCommand(command: RaftCommand): RaftResult {
        return when (command.operation) {
            RaftOp.PUT -> {
                requireNotNull(command.key) { "Command PUT should contain a key" }
                val propertyValue = deserializePropertyValue(command.value)
                store[command.key] = propertyValue

                publishUpdateFailSafe(command.key, propertyValue)
                
                RaftResult(
                    result = EMPTY_BYTES,
                    status = RaftResultStatus.OK
                )
            }

            RaftOp.GET -> {
                requireNotNull(command.key) { "Command GET should contain a key" }
                store[command.key]?.let {
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
                requireNotNull(command.key) { "Command DELETE should contain a key" }
                store.remove(command.key)?.let {
                    publishUpdateFailSafe(command.key, null)
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

    private fun publishUpdateFailSafe(
        key: PropertyKey,
        propertyValue: PropertyValue?
    ) {
        try {
            propertyUpdatePublisher.publishUpdate(key, propertyValue)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish update event for key: $key" }
        }
    }
}

