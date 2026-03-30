package com.fyordo.cms.server.service.raft

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import com.fyordo.cms.server.serialization.property.*
import com.fyordo.cms.server.serialization.query.fromPropertyQueryFilterProto
import com.fyordo.cms.server.serialization.raft.deserializeRaftCommand
import com.fyordo.cms.server.serialization.raft.raftCommandKey
import com.fyordo.cms.server.serialization.raft.raftErrorResult
import com.fyordo.cms.server.serialization.raft.raftNotFoundResult
import com.fyordo.cms.server.serialization.raft.raftOkResult
import com.fyordo.cms.server.serialization.raft.serializeRaftResult
import com.fyordo.cms.server.service.PropertyUpdatePublisher
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import com.fyordo.cms.server.utils.EMPTY_BYTES
import kotlinx.coroutines.*
import kotlinx.coroutines.future.future
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
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString
import org.springframework.stereotype.Component
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}
private const val SNAPSHOT_FORMAT_VERSION = 1

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

            Files.move(
                tmpFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )

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

    private fun loadSnapshotIfExists() {
        val snapshot = snapshotStorage.findLatestSnapshot() ?: run {
            logger.info { "No snapshot found - full log replay will populate state" }
            return
        }

        val file = snapshot.file.path.toFile()
        if (!file.exists()) {
            logger.warn { "Snapshot file not found at $file - falling back to log replay" }
            return
        }

        try {
            val entries = mutableListOf<Pair<PropertyKey, PropertyValue>>()
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
                    entries += deserializePropertyKey(keyBytes) to deserializePropertyValue(valueBytes)
                }
            }

            store.restoreFromSnapshot(entries, revision)
            updateLastAppliedTermIndex(snapshot.term, snapshot.index)

            logger.info {
                "Snapshot loaded: term=${snapshot.term} index=${snapshot.index} " +
                        "revision=$revision entries=${entries.size}"
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load snapshot from $file - falling back to full log replay" }
            store.restoreFromSnapshot(emptyList(), 0L)
        }
    }

    override fun close() {
        logger.info { "RaftStateMachine is closing" }
        try {
            runBlocking {
                scope.cancel("Cancelling CoroutineScope")
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
                    .toByteArray()

                val command = try {
                    deserializeRaftCommand(logData)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to deserialize Raft command. Log data length: ${logData.size}" }
                    throw e
                }

                logger.debug { "Applying: ${command.operation} ${command.key} logIndex=$logIndex" }
                processCommand(command, logIndex)
                    .let(::serializeRaftResult)
                    .let { Message.valueOf(ByteString.copyFrom(it)) }
            }.getOrElse { e ->
                logger.error(e) { "Error applying transaction at logIndex=$logIndex: ${e.javaClass.simpleName}: ${e.message}" }
                Message.valueOf(
                    ByteString.copyFrom(
                        serializeRaftResult(raftErrorResult())
                    )
                )
            }
        }

    override fun query(request: Message): CompletableFuture<Message> =
        scope.future {
            runCatching {
                val requestContent = request.content.toByteArray()

                val command = try {
                    deserializeRaftCommand(requestContent)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to deserialize Raft query command. Content length: ${requestContent.size}" }
                    throw e
                }

                logger.debug { "Query: ${command.operation} ${command.key}" }
                processCommand(command)
                    .let(::serializeRaftResult)
                    .let { Message.valueOf(ByteString.copyFrom(it)) }
            }.getOrElse { e ->
                logger.error(e) { "Error processing query: ${e.javaClass.simpleName}: ${e.message}" }
                Message.valueOf(
                    ByteString.copyFrom(
                        serializeRaftResult(raftErrorResult())
                    )
                )
            }
        }

    private fun processCommand(
        command: CmsProto.RaftCommandProto,
        logIndex: Long = 0L
    ): CmsProto.RaftResultProto {
        val commandKey = raftCommandKey(command)
        return when (command.operation) {
            CmsProto.RaftOpProto.RAFT_OP_PUT -> {
                val key = requireNotNull(commandKey) { "Command PUT should contain a key" }
                val propertyValue = deserializePropertyValue(command.value.toByteArray())
                store.setWithRevision(key, propertyValue, logIndex)
                publishUpdateFailSafe(key, propertyValue, logIndex)
                raftOkResult()
            }

            CmsProto.RaftOpProto.RAFT_OP_GET -> {
                val key = requireNotNull(commandKey) { "Command GET should contain a key" }
                store[key]?.let {
                    raftOkResult(serializePropertyValue(it))
                } ?: raftNotFoundResult()
            }

            CmsProto.RaftOpProto.RAFT_OP_DELETE -> {
                val key = requireNotNull(commandKey) { "Command DELETE should contain a key" }
                store.removeWithRevision(key, logIndex)?.let {
                    publishUpdateFailSafe(key, null, logIndex)
                    raftOkResult()
                } ?: raftNotFoundResult()
            }

            CmsProto.RaftOpProto.RAFT_OP_QUERY -> {
                val filter = fromPropertyQueryFilterProto(
                    CmsProto.PropertyQueryFilterProto.parseFrom(command.value)
                )
                val resultBytes = serializePropertyInternalDtoList(store.getByFilter(filter).toList())
                raftOkResult(resultBytes)
            }

            CmsProto.RaftOpProto.RAFT_OP_UNSPECIFIED -> raftErrorResult()
            else -> raftErrorResult()
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
}

