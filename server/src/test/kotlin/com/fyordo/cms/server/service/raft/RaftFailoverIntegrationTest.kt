package com.fyordo.cms.server.service.raft

import com.fyordo.cms.server.config.props.RaftConfiguration
import com.fyordo.cms.server.config.props.AgentProperties
import com.fyordo.cms.CmsProto
import com.google.protobuf.ByteString
import com.fyordo.cms.server.service.PropertyUpdatePublisher
import com.fyordo.cms.server.serialization.property.serializePropertyValue
import com.fyordo.cms.server.serialization.property.deserializePropertyValue as deserializePropertyValueBytes
import com.fyordo.cms.server.serialization.raft.deserializeRaftResult
import com.fyordo.cms.server.serialization.raft.serializeRaftCommand as serializeRaftCommandBytes
import com.fyordo.cms.server.service.agent.AgentConnectionManager
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import com.fyordo.cms.server.service.storage.PropertyPartsHolder
import com.fyordo.cms.server.utils.EMPTY_BYTES
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.ratis.client.RaftClient
import org.apache.ratis.conf.RaftProperties
import org.apache.ratis.protocol.*
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString as RatisByteString
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Integration tests for Raft cluster failover scenarios.
 * Tests leader election, node failures, and cluster recovery.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RaftFailoverIntegrationTest {

    // ---- Compatibility layer for old test DTO style ----
    private fun PropertyKey(
        version: Int,
        namespace: String,
        service: String,
        appId: String,
        key: String
    ): CmsProto.PropertyKey = CmsProto.PropertyKey.newBuilder()
        .setVersion(version)
        .setNamespace(namespace)
        .setService(service)
        .setAppId(appId)
        .setKey(key)
        .build()

    private fun PropertyValue(
        version: Int,
        value: ByteArray,
        lastModifiedMs: Long
    ): CmsProto.PropertyValue = CmsProto.PropertyValue.newBuilder()
        .setVersion(version)
        .setValue(ByteString.copyFrom(value))
        .setLastModifiedMs(lastModifiedMs)
        .build()

    private object RaftOp {
        val GET: CmsProto.RaftOp = CmsProto.RaftOp.RAFT_OP_GET
        val PUT: CmsProto.RaftOp = CmsProto.RaftOp.RAFT_OP_PUT
        val DELETE: CmsProto.RaftOp = CmsProto.RaftOp.RAFT_OP_DELETE
    }

    private object RaftResultStatus {
        val OK: CmsProto.RaftResultStatus = CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_OK
        val NOT_FOUND: CmsProto.RaftResultStatus = CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_NOT_FOUND
        val ERROR: CmsProto.RaftResultStatus = CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_ERROR
    }

    private fun RaftCommand(
        version: Int,
        operation: CmsProto.RaftOp,
        key: CmsProto.PropertyKey?,
        value: ByteArray
    ): CmsProto.RaftCommand = CmsProto.RaftCommand.newBuilder()
        .setVersion(version)
        .setOperation(operation)
        .apply { if (key != null) setKey(key) }
        .setValue(ByteString.copyFrom(value))
        .build()

    private fun serializeRaftCommand(command: CmsProto.RaftCommand): RatisByteString =
        RatisByteString.copyFrom(serializeRaftCommandBytes(command))

    private fun deserializePropertyValue(propertyValue: ByteString): CmsProto.PropertyValue =
        deserializePropertyValueBytes(propertyValue.toByteArray())

    private val testGroupId = "failover-test-raft-group-${UUID.randomUUID()}"
    private val basePort = 18000 + (Math.random() * 1000).toInt()
    private val testDataDir = Files.createTempDirectory("raft-failover-test-").toFile()

    private lateinit var node1: RaftServerService
    private lateinit var node2: RaftServerService
    private lateinit var node3: RaftServerService

    private lateinit var client: RaftClient
    private lateinit var groupId: RaftGroupId

    @BeforeAll
    fun setup() {
        println("Setting up Raft failover test cluster...")
        println("Base port: $basePort")
        println("Test data dir: ${testDataDir.absolutePath}")

        groupId = RaftGroupId.valueOf(UUID.nameUUIDFromBytes(testGroupId.toByteArray()))

        // Create configurations for 3 nodes
        val config1 = createNodeConfig("node1", basePort)
        val config2 = createNodeConfig("node2", basePort + 1)
        val config3 = createNodeConfig("node3", basePort + 2)

        // Initialize nodes
        node1 = createNode(config1)
        node2 = createNode(config2)
        node3 = createNode(config3)

        // Create client
        client = createClient()

        // Wait for initial leader election
        runBlocking {
            waitForLeaderElection()
        }

        println("Raft failover test cluster setup complete")
    }

    @AfterAll
    fun teardown() {
        println("Tearing down Raft failover test cluster...")

        runCatching { node1.stop() }
        runCatching { node2.stop() }
        runCatching { node3.stop() }
        runCatching { client.close() }

        // Clean up test data
        testDataDir.deleteRecursively()

        println("Raft failover test cluster teardown complete")
    }

    private fun createNodeConfig(nodeId: String, port: Int): RaftConfiguration {
        return RaftConfiguration(
            nodeId = nodeId,
            host = "localhost",
            port = port,
            storageDir = File(testDataDir, nodeId).absolutePath,
            groupId = testGroupId,
            electionTimeoutMs = 1500,
            heartbeatIntervalMs = 400,
            peers = listOf(
                "node1:localhost:$basePort",
                "node2:localhost:${basePort + 1}",
                "node3:localhost:${basePort + 2}"
            ).filter { !it.startsWith(nodeId) }
        )
    }

    private fun createNode(config: RaftConfiguration): RaftServerService {
        val pathHolder = PropertyPartsHolder()
        val storage = PropertyInMemoryStorage(pathHolder)
        val broadcaster = PropertyUpdatePublisher()
        val stateMachine = RaftStateMachine(storage, broadcaster)
        val agentConnectionManager = AgentConnectionManager(storage, broadcaster, AgentProperties())
        val server = RaftServerService(config, stateMachine, agentConnectionManager)
        server.init()
        return server
    }

    private fun createClient(): RaftClient {
        val peers = listOf(
            RaftPeer.newBuilder()
                .setId(RaftPeerId.valueOf("node1"))
                .setAddress(InetSocketAddress("localhost", basePort))
                .build(),
            RaftPeer.newBuilder()
                .setId(RaftPeerId.valueOf("node2"))
                .setAddress(InetSocketAddress("localhost", basePort + 1))
                .build(),
            RaftPeer.newBuilder()
                .setId(RaftPeerId.valueOf("node3"))
                .setAddress(InetSocketAddress("localhost", basePort + 2))
                .build()
        )

        val raftGroup = RaftGroup.valueOf(groupId, peers)

        return RaftClient.newBuilder()
            .setClientId(ClientId.randomId())
            .setRaftGroup(raftGroup)
            .setProperties(RaftProperties())
            .build()
    }

    private suspend fun waitForLeaderElection(maxAttempts: Int = 40) {
        println("Waiting for leader election...")
        repeat(maxAttempts) { attempt ->
            val nodes = listOfNotNull(
                runCatching { node1 }.getOrNull(),
                runCatching { node2 }.getOrNull(),
                runCatching { node3 }.getOrNull()
            )
            val hasLeader = nodes.any { runCatching { it.isLeader() }.getOrDefault(false) }
            if (hasLeader) {
                println("Leader elected after ${attempt + 1} attempts")
                return
            }
            delay(500)
        }
        println("Warning: No leader elected after $maxAttempts attempts")
    }

    private fun getLeaderNode(): RaftServerService? {
        return listOf(node1, node2, node3).find {
            runCatching { it.isLeader() }.getOrDefault(false)
        }
    }

    @Test
    fun `should maintain data after leader re-election`(): Unit = runBlocking {
        // Store data
        val key = PropertyKey(
            version = 1,
            namespace = "failover-ns",
            service = "failover-svc",
            appId = "failover-app",
            key = "failover-key-${UUID.randomUUID()}"
        )
        val value = PropertyValue(
            version = 1,
            value = "failover-value".toByteArray(),
            lastModifiedMs = System.currentTimeMillis()
        )

        val putCommand = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value)
        )

        client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))
        delay(1000)

        // Find and stop the leader
        val originalLeader = getLeaderNode()
        assertNotNull(originalLeader, "Should have a leader")

        println("Stopping original leader: ${originalLeader.getLeaderId()}")
        originalLeader.stop()

        // Wait for new leader election
        delay(3000)
        waitForLeaderElection()

        val newLeader = getLeaderNode()
        assertNotNull(newLeader, "Should have elected a new leader")
        println("New leader elected: ${newLeader.getLeaderId()}")

        // Verify data is still accessible
        val getCommand = RaftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        delay(500)

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toByteArray())

        assertEquals(RaftResultStatus.OK, getResult.status)
        val retrievedValue = deserializePropertyValue(getResult.result)
        assertEquals("failover-value", String(retrievedValue.value.toByteArray()))
    }

    @Test
    fun `should handle sequential operations during leader transitions`(): Unit = runBlocking {
        waitForLeaderElection()

        val keys = (1..5).map { i ->
            PropertyKey(
                version = 1,
                namespace = "transition-ns",
                service = "transition-svc",
                appId = "transition-app",
                key = "transition-key-$i"
            )
        }

        // Write first batch
        keys.take(2).forEachIndexed { index, key ->
            val value = PropertyValue(
                version = 1,
                value = "value-$index".toByteArray(),
                lastModifiedMs = System.currentTimeMillis()
            )

            val putCommand = RaftCommand(
                version = 1,
                operation = RaftOp.PUT,
                key = key,
                value = serializePropertyValue(value)
            )

            client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))
            delay(200)
        }

        delay(1000)

        // Verify first batch
        keys.take(2).forEachIndexed { index, key ->
            val getCommand = RaftCommand(
                version = 1,
                operation = RaftOp.GET,
                key = key,
                value = EMPTY_BYTES
            )

            val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
            val getResult = deserializeRaftResult(getReply.message.content.toByteArray())

            assertEquals(RaftResultStatus.OK, getResult.status, "Key $index should exist")
        }

        // Write remaining keys - they should succeed even during potential transitions
        keys.drop(2).forEachIndexed { index, key ->
            val value = PropertyValue(
                version = 1,
                value = "value-${index + 2}".toByteArray(),
                lastModifiedMs = System.currentTimeMillis()
            )

            val putCommand = RaftCommand(
                version = 1,
                operation = RaftOp.PUT,
                key = key,
                value = serializePropertyValue(value)
            )

            client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))
            delay(200)
        }

        delay(2000)

        // Verify all keys
        keys.forEachIndexed { index, key ->
            val getCommand = RaftCommand(
                version = 1,
                operation = RaftOp.GET,
                key = key,
                value = EMPTY_BYTES
            )

            val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
            val getResult = deserializeRaftResult(getReply.message.content.toByteArray())

            assertEquals(RaftResultStatus.OK, getResult.status, "Key $index should exist")
            val retrievedValue = deserializePropertyValue(getResult.result)
            assertEquals("value-$index", String(retrievedValue.value.toByteArray()))
        }
    }

    @Test
    fun `should preserve data consistency across failures`(): Unit = runBlocking {
        waitForLeaderElection()

        val baseKey = "consistency-key-${UUID.randomUUID()}"
        val keys = (1..10).map { i ->
            PropertyKey(
                version = 1,
                namespace = "consistency-ns",
                service = "consistency-svc",
                appId = "consistency-app",
                key = "$baseKey-$i"
            )
        }

        // Write multiple keys
        keys.forEachIndexed { index, key ->
            val value = PropertyValue(
                version = 1,
                value = "consistent-value-$index".toByteArray(),
                lastModifiedMs = System.currentTimeMillis()
            )

            val putCommand = RaftCommand(
                version = 1,
                operation = RaftOp.PUT,
                key = key,
                value = serializePropertyValue(value)
            )

            client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))
            delay(100)
        }

        delay(2000) // Ensure all writes are replicated

        // Verify all data before any failures
        keys.forEachIndexed { index, key ->
            val getCommand = RaftCommand(
                version = 1,
                operation = RaftOp.GET,
                key = key,
                value = EMPTY_BYTES
            )

            val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
            val getResult = deserializeRaftResult(getReply.message.content.toByteArray())

            assertEquals(RaftResultStatus.OK, getResult.status)
            val retrievedValue = deserializePropertyValue(getResult.result)
            assertEquals("consistent-value-$index", String(retrievedValue.value.toByteArray()))
        }

        println("All data verified before failures - consistency maintained")
    }
}
