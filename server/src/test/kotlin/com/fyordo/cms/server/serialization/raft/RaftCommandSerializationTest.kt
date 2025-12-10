package com.fyordo.cms.server.serialization.raft

import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.raft.RaftCommand
import com.fyordo.cms.server.dto.raft.RaftOp
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RaftCommandSerializationTest {

    @Test
    fun `should serialize and deserialize RaftCommand with GET operation and key`() {
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = PropertyKey(
                version = 1,
                namespace = "test-ns",
                service = "test-svc",
                appId = "test-app",
                key = "test-key"
            ),
            value = ByteArray(0)
        )

        val serialized = serializeRaftCommand(command)
        val deserialized = deserializeRaftCommand(serialized)

        assertEquals(command, deserialized)
    }

    @Test
    fun `should serialize and deserialize RaftCommand with PUT operation`() {
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = PropertyKey(
                version = 1,
                namespace = "ns",
                service = "svc",
                appId = "app",
                key = "key"
            ),
            value = "test-value".toByteArray()
        )

        val serialized = serializeRaftCommand(command)
        val deserialized = deserializeRaftCommand(serialized)

        assertEquals(command.version, deserialized.version)
        assertEquals(command.operation, deserialized.operation)
        assertEquals(command.key, deserialized.key)
        assertContentEquals(command.value, deserialized.value)
    }

    @Test
    fun `should serialize and deserialize RaftCommand with DELETE operation`() {
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.DELETE,
            key = PropertyKey(
                version = 1,
                namespace = "delete-ns",
                service = "delete-svc",
                appId = "delete-app",
                key = "delete-key"
            ),
            value = ByteArray(0)
        )

        val serialized = serializeRaftCommand(command)
        val deserialized = deserializeRaftCommand(serialized)

        assertEquals(command, deserialized)
    }

    @Test
    fun `should serialize and deserialize RaftCommand with QUERY operation`() {
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.QUERY,
            key = null,
            value = "query-filter-data".toByteArray()
        )

        val serialized = serializeRaftCommand(command)
        val deserialized = deserializeRaftCommand(serialized)

        assertEquals(command.version, deserialized.version)
        assertEquals(command.operation, deserialized.operation)
        assertNull(deserialized.key)
        assertContentEquals(command.value, deserialized.value)
    }

    @Test
    fun `should serialize and deserialize RaftCommand with null key`() {
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.QUERY,
            key = null,
            value = "some-data".toByteArray()
        )

        val serialized = serializeRaftCommand(command)
        val deserialized = deserializeRaftCommand(serialized)

        assertEquals(command.version, deserialized.version)
        assertEquals(command.operation, deserialized.operation)
        assertNull(deserialized.key)
        assertContentEquals(command.value, deserialized.value)
    }

    @Test
    fun `should serialize and deserialize RaftCommand with empty value`() {
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = PropertyKey(
                version = 1,
                namespace = "ns",
                service = "svc",
                appId = "app",
                key = "key"
            ),
            value = ByteArray(0)
        )

        val serialized = serializeRaftCommand(command)
        val deserialized = deserializeRaftCommand(serialized)

        assertEquals(command, deserialized)
    }

    @Test
    fun `should serialize and deserialize RaftCommand with binary value`() {
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = PropertyKey(
                version = 1,
                namespace = "binary-ns",
                service = "binary-svc",
                appId = "binary-app",
                key = "binary-key"
            ),
            value = byteArrayOf(0, 1, 127, -128, -1, 42)
        )

        val serialized = serializeRaftCommand(command)
        val deserialized = deserializeRaftCommand(serialized)

        assertEquals(command.version, deserialized.version)
        assertEquals(command.operation, deserialized.operation)
        assertEquals(command.key, deserialized.key)
        assertContentEquals(command.value, deserialized.value)
    }

    @Test
    fun `should serialize and deserialize RaftCommand with large value`() {
        val largeValue = ByteArray(100000) { it.toByte() }
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = PropertyKey(
                version = 1,
                namespace = "large-ns",
                service = "large-svc",
                appId = "large-app",
                key = "large-key"
            ),
            value = largeValue
        )

        val serialized = serializeRaftCommand(command)
        val deserialized = deserializeRaftCommand(serialized)

        assertEquals(command.version, deserialized.version)
        assertEquals(command.operation, deserialized.operation)
        assertEquals(command.key, deserialized.key)
        assertContentEquals(command.value, deserialized.value)
    }

    @Test
    fun `should serialize and deserialize RaftCommand with UTF-8 data`() {
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = PropertyKey(
                version = 1,
                namespace = "命名空间",
                service = "сервис",
                appId = "アプリ",
                key = "مفتاح"
            ),
            value = "значение 🎉".toByteArray(Charsets.UTF_8)
        )

        val serialized = serializeRaftCommand(command)
        val deserialized = deserializeRaftCommand(serialized)

        assertEquals(command.version, deserialized.version)
        assertEquals(command.operation, deserialized.operation)
        assertEquals(command.key, deserialized.key)
        assertContentEquals(command.value, deserialized.value)
    }

    @Test
    fun `should serialize all RaftOp operations correctly`() {
        RaftOp.entries.forEach { operation ->
            val command = RaftCommand(
                version = 1,
                operation = operation,
                key = if (operation != RaftOp.QUERY) {
                    PropertyKey(
                        version = 1,
                        namespace = "ns",
                        service = "svc",
                        appId = "app",
                        key = "key"
                    )
                } else null,
                value = "test".toByteArray()
            )

            val serialized = serializeRaftCommand(command)
            val deserialized = deserializeRaftCommand(serialized)

            assertEquals(
                operation, deserialized.operation,
                "Operation $operation not preserved after serialization"
            )
        }
    }

    @Test
    fun `should produce Base64-encoded string`() {
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.GET,
            key = PropertyKey(
                version = 1,
                namespace = "ns",
                service = "svc",
                appId = "app",
                key = "key"
            ),
            value = ByteArray(0)
        )

        val serialized = serializeRaftCommand(command)

        // Base64 should only contain A-Z, a-z, 0-9, +, /, and = for padding
        assert(serialized.matches(Regex("^[A-Za-z0-9+/]+=*$"))) {
            "Serialized command should be valid Base64"
        }
    }

    @Test
    fun `should throw NotImplementedError for unsupported version during serialization`() {
        val command = RaftCommand(
            version = 2,
            operation = RaftOp.GET,
            key = PropertyKey(
                version = 1,
                namespace = "ns",
                service = "svc",
                appId = "app",
                key = "key"
            ),
            value = ByteArray(0)
        )

        assertThrows<NotImplementedError> {
            serializeRaftCommand(command)
        }
    }

    @Test
    fun `should throw IllegalStateException when serializing version 1 with wrong version`() {
        val command = RaftCommand(
            version = 2,
            operation = RaftOp.GET,
            key = PropertyKey(
                version = 1,
                namespace = "ns",
                service = "svc",
                appId = "app",
                key = "key"
            ),
            value = ByteArray(0)
        )

        assertThrows<IllegalStateException> {
            serializeRaftCommandV1(command)
        }
    }

    @Test
    fun `should serialize and deserialize RaftCommand v1 directly`() {
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = PropertyKey(
                version = 1,
                namespace = "direct-ns",
                service = "direct-svc",
                appId = "direct-app",
                key = "direct-key"
            ),
            value = "direct-value".toByteArray()
        )

        val serialized = serializeRaftCommandV1(command)
        val deserialized = deserializeRaftCommand(serialized)

        assertEquals(command, deserialized)
    }

    @Test
    fun `should handle multiple round trips without data corruption`() {
        val command = RaftCommand(
            version = 1,
            operation = RaftOp.PUT,
            key = PropertyKey(
                version = 1,
                namespace = "ns",
                service = "svc",
                appId = "app",
                key = "key"
            ),
            value = "test-value".toByteArray()
        )

        var serialized = serializeRaftCommand(command)
        repeat(5) {
            val deserialized = deserializeRaftCommand(serialized)
            serialized = serializeRaftCommand(deserialized)
        }
        val final = deserializeRaftCommand(serialized)

        assertEquals(command.version, final.version)
        assertEquals(command.operation, final.operation)
        assertEquals(command.key, final.key)
        assertContentEquals(command.value, final.value)
    }
}
