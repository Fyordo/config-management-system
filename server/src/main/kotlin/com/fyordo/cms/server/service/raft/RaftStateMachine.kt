package com.fyordo.cms.server.service.raft

import com.fyordo.cms.CmsProto
import com.fyordo.cms.server.config.CmsMetrics
import com.fyordo.cms.server.serialization.property.deserializePropertyKey
import com.fyordo.cms.server.serialization.property.deserializePropertyValue
import com.fyordo.cms.server.serialization.property.serializePropertyKey
import com.fyordo.cms.server.serialization.property.serializePropertyValue
import com.fyordo.cms.server.serialization.raft.*
import com.fyordo.cms.server.service.PropertyUpdatePublisher
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import io.micrometer.core.instrument.Timer
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
    private val propertyUpdatePublisher: PropertyUpdatePublisher,
    private val metrics: CmsMetrics
) : BaseStateMachine() {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("RaftStateMachine")
    )
    private val snapshotStorage = SimpleStateMachineStorage()

    override fun initialize(
        server: RaftServer,
        groupId: RaftGroupId,
        storage: RaftStorage
    ) {
        super.initialize(server, groupId, storage)
        snapshotStorage.init(storage)
        loadSnapshotIfExists()
        logger.info { "RaftStateMachine initialized for group=[${groupId.uuid}]" }
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

        return runCatching {
            val (revision, entries) = store.getSnapshotData()

            DataOutputStream(BufferedOutputStream(tmpFile.outputStream())).use { out ->
                out.writeInt(SNAPSHOT_FORMAT_VERSION)
                out.writeLong(revision)
                out.writeInt(entries.size)
                for (entry in entries) {
                    val keyBytes = serializePropertyKey(entry.key)
                    out.writeInt(keyBytes.size)
                    out.write(keyBytes)
                    val valueBytes = serializePropertyValue(entry.value)
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

            metrics.raftSnapshotTotal.increment()
            logger.info { "Snapshot taken: logIndex=[$index] revision=[$revision] entriesCount=[${entries.size}]" }
            index
        }.onFailure { e ->
            tmpFile.delete()
            logger.error(e) { "Failed to take snapshot at logIndex=[$index]" }
        }.getOrDefault(RaftLog.INVALID_LOG_INDEX)
    }

    override fun getLatestSnapshot(): SnapshotInfo? = runCatching {
        snapshotStorage.latestSnapshot
    }.onFailure { e ->
        logger.warn(e) { "Failed to locate latest snapshot" }
    }.getOrNull()

    private fun loadSnapshotIfExists() {
        val snapshot = snapshotStorage.latestSnapshot ?: run {
            logger.info { "No snapshot found - full log replay will populate state" }
            return
        }

        val file = snapshot.file.path.toFile()
        if (!file.exists()) {
            logger.warn { "Snapshot file not found at [$file] - falling back to log replay" }
            return
        }

        try {
            val entries = mutableListOf<CmsProto.PropertyInternalDto>()
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
                    entries += CmsProto.PropertyInternalDto.newBuilder()
                        .setKey(deserializePropertyKey(keyBytes))
                        .setValue(deserializePropertyValue(valueBytes))
                        .build()
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
            metrics.raftApplyTotal.increment()
            val sample = Timer.start()
            runCatching {
                val logData = trx.logEntry
                    .stateMachineLogEntry
                    .logData
                    .toByteArray()

                val command = try {
                    deserializeRaftCommand(logData)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to deserialize Raft command with length=[${logData.size}]" }
                    throw e
                }

                logger.debug { "Applying: [${command.operation}] [${command.key}] logIndex=[$logIndex]" }
                processCommand(command, logIndex)
                    .let(::serializeRaftResult)
                    .let { Message.valueOf(ByteString.copyFrom(it)) }
            }.also {
                sample.stop(metrics.raftApplyTimer)
            }.getOrElse { e ->
                metrics.raftApplyErrorTotal.increment()
                logger.error(e) { "Error applying transaction at logIndex=[$logIndex]" }
                Message.valueOf(
                    ByteString.copyFrom(
                        serializeRaftResult(raftErrorResult())
                    )
                )
            }
        }

    override fun query(request: Message): CompletableFuture<Message> =
        scope.future {
            metrics.raftQueryTotal.increment()
            runCatching {
                val requestContent = request.content.toByteArray()

                val command = try {
                    deserializeRaftCommand(requestContent)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to deserialize Raft query command with length=[${requestContent.size}]" }
                    throw e
                }

                logger.debug { "Query: ${command.operation} ${command.key}" }
                processCommand(command)
                    .let(::serializeRaftResult)
                    .let { Message.valueOf(ByteString.copyFrom(it)) }
            }.getOrElse { e ->
                metrics.raftQueryErrorTotal.increment()
                logger.error(e) { "Error processing query" }
                Message.valueOf(
                    ByteString.copyFrom(
                        serializeRaftResult(raftErrorResult())
                    )
                )
            }
        }

    private fun processCommand(
        command: CmsProto.RaftCommand,
        logIndex: Long = 0L
    ): CmsProto.RaftResult {
        val commandKey = command.key

        return when (command.operation) {
            CmsProto.RaftOp.RAFT_OP_PUT -> {
                val key = requireNotNull(commandKey) { "Command PUT should contain a key" }
                val propertyValue = deserializePropertyValue(command.value.toByteArray())
                store.setWithRevision(key, propertyValue, logIndex)
                publishUpdateFailSafe(key, propertyValue, logIndex)
                metrics.raftPutTotal.increment()
                raftOkResult()
            }

            CmsProto.RaftOp.RAFT_OP_DELETE -> {
                val key = requireNotNull(commandKey) { "Command DELETE should contain a key" }
                store.removeWithRevision(key, logIndex)?.let {
                    publishUpdateFailSafe(key, null, logIndex)
                    metrics.raftDeleteTotal.increment()
                    raftOkResult()
                } ?: raftNotFoundResult()
            }

            else -> raftErrorResult()
        }
    }

    private fun publishUpdateFailSafe(
        key: CmsProto.PropertyKey,
        propertyValue: CmsProto.PropertyValue?,
        revision: Long
    ) {
        try {
            propertyUpdatePublisher.publishUpdate(key, propertyValue, revision)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to publish update event for key: $key revision: $revision" }
        }
    }
}

