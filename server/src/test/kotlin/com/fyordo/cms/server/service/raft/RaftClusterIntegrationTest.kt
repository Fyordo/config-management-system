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
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RaftClusterIntegrationTest {
    private fun propertyKey(
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

    private fun propertyValue(
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
        val QUERY: CmsProto.RaftOp = CmsProto.RaftOp.RAFT_OP_QUERY
    }

    private object RaftResultStatus {
        val OK: CmsProto.RaftResultStatus = CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_OK
        val NOT_FOUND: CmsProto.RaftResultStatus = CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_NOT_FOUND
        val ERROR: CmsProto.RaftResultStatus = CmsProto.RaftResultStatus.RAFT_RESULT_STATUS_ERROR
    }

    private fun raftCommand(
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

    // Ratis Message.valueOf expects ByteString, while our production serializer returns ByteArray.
    private fun serializeRaftCommand(command: CmsProto.RaftCommand): RatisByteString =
        RatisByteString.copyFrom(serializeRaftCommandBytes(command))

    private fun deserializePropertyValue(propertyValue: ByteString): CmsProto.PropertyValue =
        deserializePropertyValueBytes(propertyValue.toByteArray())

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
        val key = propertyKey(
            version = 1,
            namespace = "test-ns",
            service = "test-svc",
            appId = "test-app",
            key = "test-key-${UUID.randomUUID()}"
        )
        val value = propertyValue(
            version = 1,
            value = "test-value".toByteArray(),
            lastModifiedMs = System.currentTimeMillis()
        )

        // PUT operation
        val putCommand = raftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value)
        )

        val putReply = client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))
        val putResult = deserializeRaftResult(putReply.message.content.toByteArray())

        assertEquals(RaftResultStatus.OK, putResult.status)

        // GET operation
        val getCommand = raftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        delay(500) // Wait for replication

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toByteArray())

        assertEquals(RaftResultStatus.OK, getResult.status)
        val retrievedValue = deserializePropertyValue(getResult.result)
        assertEquals("test-value", String(retrievedValue.value.toByteArray()))
    }

    @Test
    fun `should handle DELETE operation through Raft`(): Unit = runBlocking {
        val key = propertyKey(
            version = 1,
            namespace = "delete-ns",
            service = "delete-svc",
            appId = "delete-app",
            key = "delete-key-${UUID.randomUUID()}"
        )
        val value = propertyValue(
            version = 1,
            value = "delete-value".toByteArray(),
            lastModifiedMs = System.currentTimeMillis()
        )

        // First, PUT the property
        val putCommand = raftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value)
        )
        client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))

        delay(500)

        // Then, DELETE it
        val deleteCommand = raftCommand(
            version = 1,
            operation = RaftOp.DELETE,
            key = key,
            value = EMPTY_BYTES
        )

        val deleteReply = client.io().send(Message.valueOf(serializeRaftCommand(deleteCommand)))
        val deleteResult = deserializeRaftResult(deleteReply.message.content.toByteArray())

        assertEquals(RaftResultStatus.OK, deleteResult.status)

        delay(500)

        // Verify it's deleted
        val getCommand = raftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toByteArray())

        assertEquals(RaftResultStatus.NOT_FOUND, getResult.status)
    }

    @Test
    fun `should return NOT_FOUND for non-existent key`(): Unit = runBlocking {
        val key = propertyKey(
            version = 1,
            namespace = "non-existent",
            service = "non-existent",
            appId = "non-existent",
            key = "non-existent-${UUID.randomUUID()}"
        )

        val getCommand = raftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toByteArray())

        assertEquals(RaftResultStatus.NOT_FOUND, getResult.status)
    }

    @Test
    fun `should update existing property`(): Unit = runBlocking {
        val key = propertyKey(
            version = 1,
            namespace = "update-ns",
            service = "update-svc",
            appId = "update-app",
            key = "update-key-${UUID.randomUUID()}"
        )

        // First value
        val value1 = propertyValue(
            version = 1,
            value = "first-value".toByteArray(),
            lastModifiedMs = System.currentTimeMillis()
        )

        val putCommand1 = raftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value1)
        )
        client.io().send(Message.valueOf(serializeRaftCommand(putCommand1)))

        delay(500)

        // Second value
        val value2 = propertyValue(
            version = 1,
            value = "second-value".toByteArray(),
            lastModifiedMs = System.currentTimeMillis()
        )

        val putCommand2 = raftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value2)
        )
        client.io().send(Message.valueOf(serializeRaftCommand(putCommand2)))

        delay(500)

        // Verify updated value
        val getCommand = raftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toByteArray())

        assertEquals(RaftResultStatus.OK, getResult.status)
        val retrievedValue = deserializePropertyValue(getResult.result)
        assertEquals("second-value", String(retrievedValue.value.toByteArray()))
    }

    @Test
    fun `should handle multiple concurrent writes`(): Unit = runBlocking {
        val keys = (1..10).map { i ->
            propertyKey(
                version = 1,
                namespace = "concurrent-ns",
                service = "concurrent-svc",
                appId = "concurrent-app",
                key = "concurrent-key-$i"
            )
        }

        // Write all keys concurrently
        keys.forEachIndexed { index, key ->
            val value = propertyValue(
                version = 1,
                value = "value-$index".toByteArray(),
                lastModifiedMs = System.currentTimeMillis()
            )

            val putCommand = raftCommand(
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
            val getCommand = raftCommand(
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
    fun `should maintain consistency across all nodes`(): Unit = runBlocking {
        val key = propertyKey(
            version = 1,
            namespace = "consistency-ns",
            service = "consistency-svc",
            appId = "consistency-app",
            key = "consistency-key-${UUID.randomUUID()}"
        )
        val value = propertyValue(
            version = 1,
            value = "consistency-value".toByteArray(),
            lastModifiedMs = System.currentTimeMillis()
        )

        // Write through Raft
        val putCommand = raftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value)
        )

        client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))

        delay(1000) // Wait for replication to all nodes

        // Read from all nodes through Raft
        val getCommand = raftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        // Multiple reads should all return the same value
        repeat(5) {
            val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
            val getResult = deserializeRaftResult(getReply.message.content.toByteArray())

            assertEquals(RaftResultStatus.OK, getResult.status)
            val retrievedValue = deserializePropertyValue(getResult.result)
            assertEquals("consistency-value", String(retrievedValue.value.toByteArray()))
        }
    }

    @Test
    fun `should handle UTF-8 data correctly`(): Unit = runBlocking {
        val key = propertyKey(
            version = 1,
            namespace = "命名空间",
            service = "сервис",
            appId = "アプリ",
            key = "مفتاح-${UUID.randomUUID()}"
        )
        val value = propertyValue(
            version = 1,
            value = "значение 🎉 with emoji".toByteArray(Charsets.UTF_8),
            lastModifiedMs = System.currentTimeMillis()
        )

        // PUT
        val putCommand = raftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value)
        )
        client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))

        delay(500)

        // GET
        val getCommand = raftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toByteArray())

        assertEquals(RaftResultStatus.OK, getResult.status)
        val retrievedValue = deserializePropertyValue(getResult.result)
        assertEquals(
            "значение 🎉 with emoji",
            String(retrievedValue.value.toByteArray(), Charsets.UTF_8)
        )
    }

    @Test
    fun `should handle large values`(): Unit = runBlocking {
        val key = propertyKey(
            version = 1,
            namespace = "large-ns",
            service = "large-svc",
            appId = "large-app",
            key = "large-key-${UUID.randomUUID()}"
        )

        val largeData = ByteArray(10000) { it.toByte() }
        val value = propertyValue(
            version = 1,
            value = largeData,
            lastModifiedMs = System.currentTimeMillis()
        )

        // PUT
        val putCommand = raftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = key,
            value = serializePropertyValue(value)
        )
        client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))

        delay(1000)

        // GET
        val getCommand = raftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = key,
            value = EMPTY_BYTES
        )

        val getReply = client.io().sendReadOnly(Message.valueOf(serializeRaftCommand(getCommand)))
        val getResult = deserializeRaftResult(getReply.message.content.toByteArray())

        assertEquals(RaftResultStatus.OK, getResult.status)
        val retrievedValue = deserializePropertyValue(getResult.result)
        assertEquals(10000, retrievedValue.value.size())
        assertTrue(largeData.contentEquals(retrievedValue.value.toByteArray()))
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
