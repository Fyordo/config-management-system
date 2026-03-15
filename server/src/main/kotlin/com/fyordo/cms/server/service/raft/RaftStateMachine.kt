package com.fyordo.cms.server.service.raft

import com.fyordo.cms.server.dto.property.PropertyInternalDto
import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import com.fyordo.cms.server.dto.raft.RaftCommand
import com.fyordo.cms.server.dto.raft.RaftOp
import com.fyordo.cms.server.dto.raft.RaftResult
import com.fyordo.cms.server.dto.raft.RaftResultStatus
import com.fyordo.cms.server.service.PropertyUpdatePublisher
import com.fyordo.cms.server.serialization.property.deserializePropertyKey
import com.fyordo.cms.server.serialization.property.deserializePropertyValue
import com.fyordo.cms.server.serialization.property.serializePropertyInternalDto
import com.fyordo.cms.server.serialization.property.serializePropertyKey
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
import org.apache.ratis.server.raftlog.RaftLog
import org.apache.ratis.server.storage.RaftStorage
import org.apache.ratis.statemachine.SnapshotInfo
import org.apache.ratis.statemachine.TransactionContext
import org.apache.ratis.statemachine.impl.BaseStateMachine
import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage
import org.apache.ratis.statemachine.impl.SingleFileSnapshotInfo
import org.springframework.stereotype.Component
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

@Component
class RaftStateMachine(
    private val store: PropertyInMemoryStorage,
    private val propertyUpdatePublisher: PropertyUpdatePublisher
) : BaseStateMachine() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("RaftStateMachine"))
    private val snapshotStorage = SimpleStateMachineStorage()

    override fun initialize(
        server: RaftServer,
        groupId: RaftGroupId,
        storage: RaftStorage
    ) {
        super.initialize(server, groupId, storage)
        snapshotStorage.init(storage)
        loadSnapshotIfExists()
        logger.info { "RaftStateMachine initialized for group: ${groupId.uuid}" }
    }

    override fun takeSnapshot(): Long {
        val termIndex = lastAppliedTermIndex
        val index = termIndex.index

        if (index <= 0) {
            logger.debug { "No committed entries yet, skipping snapshot" }
            return RaftLog.INVALID_LOG_INDEX
        }

        val targetFile: File = snapshotStorage.getSnapshotFile(termIndex.term, index)
        val tmpFile = File(targetFile.parent, "${targetFile.name}.tmp")

        return try {
            val (revision, entries) = store.getSnapshotData()

            DataOutputStream(BufferedOutputStream(tmpFile.outputStream())).use { out ->
                out.writeInt(SNAPSHOT_FORMAT_VERSION)
                out.writeLong(revision)
                out.writeInt(entries.size)
                for ((key, value) in entries) {
                    val keyBytes = serializePropertyKey(key)
                    out.writeInt(keyBytes.size)
                    out.write(keyBytes)
                    val valueBytes = serializePropertyValue(value)
                    out.writeInt(valueBytes.size)
                    out.write(valueBytes)
                }
            }

            Files.move(tmpFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)

            logger.info { "Snapshot taken: logIndex=$index revision=$revision entries=${entries.size}" }
            index
        } catch (e: Exception) {
            tmpFile.delete()
            logger.error(e) { "Failed to take snapshot at logIndex=$index" }
            RaftLog.INVALID_LOG_INDEX
        }
    }

    override fun getLatestSnapshot(): SnapshotInfo? =
        try {
            snapshotStorage.findLatestSnapshot()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to locate latest snapshot" }
            null
        }

    // ── Snapshot: загрузка при старте ─────────────────────────────────────────

    private fun loadSnapshotIfExists() {
        val snapshot: SingleFileSnapshotInfo = snapshotStorage.findLatestSnapshot() ?: run {
            logger.info { "No snapshot found — full log replay will populate state" }
            return
        }

        val file = snapshot.file.path.toFile()
        if (!file.exists()) {
            logger.warn { "Snapshot file not found at $file — falling back to log replay" }
            return
        }

        try {
            val entries = mutableListOf<PropertyInternalDto>()
            val revision: Long

            DataInputStream(BufferedInputStream(file.inputStream())).use { din ->
                val formatVersion = din.readInt()
                check(formatVersion == SNAPSHOT_FORMAT_VERSION) {
                    "Unsupported snapshot format version: $formatVersion (expected $SNAPSHOT_FORMAT_VERSION)"
                }

                revision = din.readLong()
                val entryCount = din.readInt()

                repeat(entryCount) {
                    val keyLen = din.readInt()
                    val keyBytes = ByteArray(keyLen).also { din.readFully(it) }
                    val valueLen = din.readInt()
                    val valueBytes = ByteArray(valueLen).also { din.readFully(it) }
                    entries += PropertyInternalDto(deserializePropertyKey(keyBytes), deserializePropertyValue(valueBytes))
                }
            }

            store.restoreFromSnapshot(entries, revision)
            // Сообщаем Ratis, какой индекс уже применён — лог будет воспроизведён только с этой точки.
            updateLastAppliedTermIndex(snapshot.term, snapshot.index)

            logger.info {
                "Snapshot loaded: term=${snapshot.term} index=${snapshot.index} " +
                "revision=$revision entries=${entries.size}"
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load snapshot from $file — falling back to full log replay" }
            store.restoreFromSnapshot(emptyList(), 0L)
        }
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
            val logIndex = trx.logEntry.index
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
                
                logger.debug { "Applying: ${command.operation} ${command.key} logIndex=$logIndex" }
                processCommand(command, logIndex)
                    .let(::serializeRaftResult)
                    .let(Message::valueOf)
            }.getOrElse { e ->
                logger.error(e) { "Error applying transaction at logIndex=$logIndex: ${e.javaClass.simpleName}: ${e.message}" }
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

    // ── Обработка команд ──────────────────────────────────────────────────────

    private fun processCommand(command: RaftCommand, logIndex: Long = 0L): RaftResult {
        return when (command.operation) {
            RaftOp.PUT -> {
                requireNotNull(command.key) { "Command PUT should contain a key" }
                val propertyValue = deserializePropertyValue(command.value)
                // Атомарно: Map + revision в одном write-lock,
                // затем событие — порядок строго гарантирован.
                store.setWithRevision(command.key, propertyValue, logIndex)
                publishUpdateFailSafe(command.key, propertyValue, logIndex)
                RaftResult(result = EMPTY_BYTES, status = RaftResultStatus.OK)
            }

            RaftOp.GET -> {
                requireNotNull(command.key) { "Command GET should contain a key" }
                store[command.key]?.let {
                    RaftResult(result = serializePropertyValue(it), status = RaftResultStatus.OK)
                } ?: RaftResult(result = EMPTY_BYTES, status = RaftResultStatus.NOT_FOUND)
            }

            RaftOp.DELETE -> {
                requireNotNull(command.key) { "Command DELETE should contain a key" }
                store.removeWithRevision(command.key, logIndex)?.let {
                    publishUpdateFailSafe(command.key, null, logIndex)
                    RaftResult(result = EMPTY_BYTES, status = RaftResultStatus.OK)
                } ?: RaftResult(result = EMPTY_BYTES, status = RaftResultStatus.NOT_FOUND)
            }

            RaftOp.QUERY -> {
                val filter = deserializePropertyQueryFilter(command.value)
                RaftResult(
                    result = serializeList(store.getByFilter(filter).toList(), ::serializePropertyInternalDto),
                    status = RaftResultStatus.OK
                )
            }
        }
    }

    private fun publishUpdateFailSafe(
        key: PropertyKey,
        propertyValue: PropertyValue?,
        revision: Long
    ) {
        try {
            propertyUpdatePublisher.publishUpdate(key, propertyValue, revision)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish update event for key: $key revision: $revision" }
        }
    }

    companion object {
        /** Версия бинарного формата снепшота. Увеличивать при изменении схемы. */
        private const val SNAPSHOT_FORMAT_VERSION = 1
    }
}

