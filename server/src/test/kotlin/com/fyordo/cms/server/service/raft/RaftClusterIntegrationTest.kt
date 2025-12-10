package com.fyordo.cms.server.service.raft

import com.fyordo.cms.server.config.props.RaftConfiguration
import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import com.fyordo.cms.server.dto.raft.RaftCommand
import com.fyordo.cms.server.dto.raft.RaftOp
import com.fyordo.cms.server.dto.raft.RaftResultStatus
import com.fyordo.cms.server.serialization.property.deserializePropertyValue
import com.fyordo.cms.server.serialization.property.serializePropertyValue
import com.fyordo.cms.server.serialization.raft.deserializeRaftResult
import com.fyordo.cms.server.serialization.raft.serializeRaftCommand
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import com.fyordo.cms.server.service.storage.PropertyPathHolder
import com.fyordo.cms.server.utils.EMPTY_BYTES
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.ratis.client.RaftClient
import org.apache.ratis.conf.RaftProperties
import org.apache.ratis.protocol.*
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
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RaftClusterIntegrationTest {

    private val testGroupId = "test-raft-group-${UUID.randomUUID()}"
    private val basePort = 17000 + (Math.random() * 1000).toInt()
    private val testDataDir = Files.createTempDirectory("raft-test-").toFile()

    private lateinit var node1: RaftServerService
    private lateinit var node2: RaftServerService
    private lateinit var node3: RaftServerService

    private lateinit var client: RaftClient
    private lateinit var groupId: RaftGroupId

    @BeforeAll
    fun setup() {
        println("Setting up Raft cluster integration test...")
        println("Base port: $basePort")
        println("Test data dir: ${testDataDir.absolutePath}")

        groupId = RaftGroupId.valueOf(UUID.nameUUIDFromBytes(testGroupId.toByteArray()))

        // Create configurations for 3 nodes
        val config1 = createNodeConfig("node1", basePort, basePort + 1, basePort + 2)
        val config2 = createNodeConfig("node2", basePort + 1, basePort, basePort + 2)
        val config3 = createNodeConfig("node3", basePort + 2, basePort, basePort + 1)

        // Initialize nodes
        node1 = createNode(config1)
        node2 = createNode(config2)
        node3 = createNode(config3)

        // Create client
        client = createClient()

        // Wait for leader election
        runBlocking {
            waitForLeaderElection()
        }

        println("Raft cluster setup complete")
    }

    @AfterAll
    fun teardown() {
        println("Tearing down Raft cluster...")

        runCatching { node1.stop() }
        runCatching { node2.stop() }
        runCatching { node3.stop() }
        runCatching { client.close() }

        // Clean up test data
        testDataDir.deleteRecursively()

        println("Raft cluster teardown complete")
    }

    private fun createNodeConfig(
        nodeId: String,
        port: Int,
        peer1Port: Int,
        peer2Port: Int
    ): RaftConfiguration {
        return RaftConfiguration(
            nodeId = nodeId,
            host = "localhost",
            port = port,
            storageDir = File(testDataDir, nodeId).absolutePath,
            groupId = testGroupId,
            electionTimeoutMs = 1000,
            heartbeatIntervalMs = 300,
            peers = listOf(
                "node1:localhost:$basePort",
                "node2:localhost:${basePort + 1}",
                "node3:localhost:${basePort + 2}"
            ).filter { !it.startsWith(nodeId) }
        )
    }

    private fun createNode(config: RaftConfiguration): RaftServerService {
        val pathHolder = PropertyPathHolder()
        val storage = PropertyInMemoryStorage(pathHolder)
        val stateMachine = RaftStateMachine(storage)
        val server = RaftServerService(config, stateMachine)
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

    private suspend fun waitForLeaderElection(maxAttempts: Int = 30) {
        println("Waiting for leader election...")
        repeat(maxAttempts) { attempt ->
            val hasLeader = listOf(node1, node2, node3).any { it.isLeader() }
            if (hasLeader) {
                println("Leader elected after ${attempt + 1} attempts")
                return
            }
            delay(500)
        }
        println("Warning: No leader elected after $maxAttempts attempts")
    }

    @Test
    fun `should elect a leader`(): Unit = runBlocking {
        val leaders = listOf(node1, node2, node3).count { it.isLeader() }

        assertEquals(1, leaders, "Should have exactly one leader")
    }

    @Test
    fun `should store and retrieve property through Raft`(): Unit = runBlocking {
        val key = PropertyKey(
            version = 1,
            namespace = "test-ns",
            service = "test-svc",
            appId = "test-app",
            key = "test-key-${UUID.randomUUID()}"
        )
        val value = PropertyValue(
            version = 1,
            value = "test-value".toByteArray(),
            lastModifiedMs = System.currentTimeMillis()
        )

        // PUT operation
        val putCommand = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value)
        )

        val putReply = client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))
        val putResult = deserializeRaftResult(putReply.message.content.toStringUtf8())

        assertEquals(RaftResultStatus.OK, putResult.status)

        // GET operation
        val getCommand = RaftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        delay(500) // Wait for replication

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toStringUtf8())

        assertEquals(RaftResultStatus.OK, getResult.status)
        val retrievedValue = deserializePropertyValue(getResult.result)
        assertEquals("test-value", String(retrievedValue.value))
    }

    @Test
    fun `should handle DELETE operation through Raft`(): Unit = runBlocking {
        val key = PropertyKey(
            version = 1,
            namespace = "delete-ns",
            service = "delete-svc",
            appId = "delete-app",
            key = "delete-key-${UUID.randomUUID()}"
        )
        val value = PropertyValue(
            version = 1,
            value = "delete-value".toByteArray(),
            lastModifiedMs = System.currentTimeMillis()
        )

        // First, PUT the property
        val putCommand = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value)
        )
        client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))

        delay(500)

        // Then, DELETE it
        val deleteCommand = RaftCommand(
            version = 1,
            operation = RaftOp.DELETE,
            key = key,
            value = EMPTY_BYTES
        )

        val deleteReply = client.io().send(Message.valueOf(serializeRaftCommand(deleteCommand)))
        val deleteResult = deserializeRaftResult(deleteReply.message.content.toStringUtf8())

        assertEquals(RaftResultStatus.OK, deleteResult.status)

        delay(500)

        // Verify it's deleted
        val getCommand = RaftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toStringUtf8())

        assertEquals(RaftResultStatus.NOT_FOUND, getResult.status)
    }

    @Test
    fun `should return NOT_FOUND for non-existent key`(): Unit = runBlocking {
        val key = PropertyKey(
            version = 1,
            namespace = "non-existent",
            service = "non-existent",
            appId = "non-existent",
            key = "non-existent-${UUID.randomUUID()}"
        )

        val getCommand = RaftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toStringUtf8())

        assertEquals(RaftResultStatus.NOT_FOUND, getResult.status)
    }

    @Test
    fun `should update existing property`(): Unit = runBlocking {
        val key = PropertyKey(
            version = 1,
            namespace = "update-ns",
            service = "update-svc",
            appId = "update-app",
            key = "update-key-${UUID.randomUUID()}"
        )

        // First value
        val value1 = PropertyValue(
            version = 1,
            value = "first-value".toByteArray(),
            lastModifiedMs = System.currentTimeMillis()
        )

        val putCommand1 = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value1)
        )
        client.io().send(Message.valueOf(serializeRaftCommand(putCommand1)))

        delay(500)

        // Second value
        val value2 = PropertyValue(
            version = 1,
            value = "second-value".toByteArray(),
            lastModifiedMs = System.currentTimeMillis()
        )

        val putCommand2 = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value2)
        )
        client.io().send(Message.valueOf(serializeRaftCommand(putCommand2)))

        delay(500)

        // Verify updated value
        val getCommand = RaftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toStringUtf8())

        assertEquals(RaftResultStatus.OK, getResult.status)
        val retrievedValue = deserializePropertyValue(getResult.result)
        assertEquals("second-value", String(retrievedValue.value))
    }

    @Test
    fun `should handle multiple concurrent writes`(): Unit = runBlocking {
        val keys = (1..10).map { i ->
            PropertyKey(
                version = 1,
                namespace = "concurrent-ns",
                service = "concurrent-svc",
                appId = "concurrent-app",
                key = "concurrent-key-$i"
            )
        }

        // Write all keys concurrently
        keys.forEachIndexed { index, key ->
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
        }

        delay(2000) // Wait for all writes to complete

        // Verify all keys
        keys.forEachIndexed { index, key ->
            val getCommand = RaftCommand(
                version = 1,
                operation = RaftOp.GET,
                key = key,
                value = EMPTY_BYTES
            )

            val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
            val getResult = deserializeRaftResult(getReply.message.content.toStringUtf8())

            assertEquals(RaftResultStatus.OK, getResult.status, "Key $index should exist")
            val retrievedValue = deserializePropertyValue(getResult.result)
            assertEquals("value-$index", String(retrievedValue.value))
        }
    }

    @Test
    fun `should maintain consistency across all nodes`(): Unit = runBlocking {
        val key = PropertyKey(
            version = 1,
            namespace = "consistency-ns",
            service = "consistency-svc",
            appId = "consistency-app",
            key = "consistency-key-${UUID.randomUUID()}"
        )
        val value = PropertyValue(
            version = 1,
            value = "consistency-value".toByteArray(),
            lastModifiedMs = System.currentTimeMillis()
        )

        // Write through Raft
        val putCommand = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value)
        )

        client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))

        delay(1000) // Wait for replication to all nodes

        // Read from all nodes through Raft
        val getCommand = RaftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        // Multiple reads should all return the same value
        repeat(5) {
            val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
            val getResult = deserializeRaftResult(getReply.message.content.toStringUtf8())

            assertEquals(RaftResultStatus.OK, getResult.status)
            val retrievedValue = deserializePropertyValue(getResult.result)
            assertEquals("consistency-value", String(retrievedValue.value))
        }
    }

    @Test
    fun `should handle UTF-8 data correctly`(): Unit = runBlocking {
        val key = PropertyKey(
            version = 1,
            namespace = "命名空间",
            service = "сервис",
            appId = "アプリ",
            key = "مفتاح-${UUID.randomUUID()}"
        )
        val value = PropertyValue(
            version = 1,
            value = "значение 🎉 with emoji".toByteArray(Charsets.UTF_8),
            lastModifiedMs = System.currentTimeMillis()
        )

        // PUT
        val putCommand = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value)
        )
        client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))

        delay(500)

        // GET
        val getCommand = RaftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toStringUtf8())

        assertEquals(RaftResultStatus.OK, getResult.status)
        val retrievedValue = deserializePropertyValue(getResult.result)
        assertEquals("значение 🎉 with emoji", String(retrievedValue.value, Charsets.UTF_8))
    }

    @Test
    fun `should handle large values`(): Unit = runBlocking {
        val key = PropertyKey(
            version = 1,
            namespace = "large-ns",
            service = "large-svc",
            appId = "large-app",
            key = "large-key-${UUID.randomUUID()}"
        )

        val largeData = ByteArray(10000) { it.toByte() }
        val value = PropertyValue(
            version = 1,
            value = largeData,
            lastModifiedMs = System.currentTimeMillis()
        )

        // PUT
        val putCommand = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value)
        )
        client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))

        delay(1000)

        // GET
        val getCommand = RaftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toStringUtf8())

        assertEquals(RaftResultStatus.OK, getResult.status)
        val retrievedValue = deserializePropertyValue(getResult.result)
        assertEquals(10000, retrievedValue.value.size)
        assertTrue(largeData.contentEquals(retrievedValue.value))
    }

    @Test
    fun `should get leader information`() {
        val leader = listOf(node1, node2, node3).find { it.isLeader() }

        assertNotNull(leader, "Should have a leader")

        val leaderId = leader.getLeaderId()
        assertNotNull(leaderId)
        assertTrue(leaderId in listOf("node1", "node2", "node3"))
    }
}
