package com.fyordo.cms.server.service.raft

import com.fyordo.cms.server.config.props.RaftConfiguration
import com.fyordo.cms.CmsDtos
import com.google.protobuf.ByteString
import com.fyordo.cms.server.service.PropertyUpdatePublisher
import com.fyordo.cms.server.serialization.property.serializePropertyValue
import com.fyordo.cms.server.serialization.property.deserializePropertyValue as deserializePropertyValueBytes
import com.fyordo.cms.server.serialization.raft.deserializeRaftResult
import com.fyordo.cms.server.serialization.raft.serializeRaftCommand as serializeRaftCommandBytes
import com.fyordo.cms.server.service.agent.AgentConnectionManager
import com.fyordo.cms.server.service.storage.PropertyInMemoryStorage
import com.fyordo.cms.server.config.CmsMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
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
    ): CmsDtos.PropertyKey = CmsDtos.PropertyKey.newBuilder()
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
    ): CmsDtos.PropertyValue = CmsDtos.PropertyValue.newBuilder()
        .setVersion(version)
        .setValue(ByteString.copyFrom(value))
        .setLastModifiedMs(lastModifiedMs)
        .build()

    private object RaftOp {
        val PUT: CmsDtos.RaftOp = CmsDtos.RaftOp.RAFT_OP_PUT
        val DELETE: CmsDtos.RaftOp = CmsDtos.RaftOp.RAFT_OP_DELETE
    }

    private object RaftResultStatus {
        val OK: CmsDtos.RaftResultStatus = CmsDtos.RaftResultStatus.RAFT_RESULT_STATUS_OK
    }

    private fun raftCommand(
        version: Int,
        operation: CmsDtos.RaftOp,
        key: CmsDtos.PropertyKey?,
        value: ByteArray
    ): CmsDtos.RaftCommand = CmsDtos.RaftCommand.newBuilder()
        .setVersion(version)
        .setOperation(operation)
        .apply { if (key != null) setKey(key) }
        .setValue(ByteString.copyFrom(value))
        .build()

    // Ratis Message.valueOf expects ByteString, while our production serializer returns ByteArray.
    private fun serializeRaftCommand(command: CmsDtos.RaftCommand): RatisByteString =
        RatisByteString.copyFrom(serializeRaftCommandBytes(command))

    private fun deserializePropertyValue(propertyValue: ByteString): CmsDtos.PropertyValue =
        deserializePropertyValueBytes(propertyValue.toByteArray())

    private val testGroupId = "test-raft-group-${UUID.randomUUID()}"
    private val basePort = 17000 + (Math.random() * 1000).toInt()
    private val testDataDir = Files.createTempDirectory("raft-test-").toFile()
    private val testRegistry = SimpleMeterRegistry()
    private val metrics = CmsMetrics.noOp()

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
        val stateMachine = RaftStateMachine(storage, broadcaster, metrics)
        val agentConnectionManager = AgentConnectionManager(storage, broadcaster, metrics, testRegistry)
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
    fun `should store property through Raft`(): Unit = runBlocking {
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

            val putReply = client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))
            val putResult = deserializeRaftResult(putReply.message.content.toByteArray())
            assertEquals(RaftResultStatus.OK, putResult.status)
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

        val putReply = client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))
        val putResult = deserializeRaftResult(putReply.message.content.toByteArray())
        assertEquals(RaftResultStatus.OK, putResult.status)
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
        val putReply = client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))
        val putResult = deserializeRaftResult(putReply.message.content.toByteArray())
        assertEquals(RaftResultStatus.OK, putResult.status)
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

        val putReply = client.io().send(Message.valueOf(serializeRaftCommand(putCommand)))
        val putResult = deserializeRaftResult(putReply.message.content.toByteArray())
        assertEquals(RaftResultStatus.OK, putResult.status)
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
