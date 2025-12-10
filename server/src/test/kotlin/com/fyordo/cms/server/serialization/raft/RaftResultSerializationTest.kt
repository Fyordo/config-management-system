package com.fyordo.cms.server.serialization.raft

import com.fyordo.cms.server.dto.raft.RaftResult
import com.fyordo.cms.server.dto.raft.RaftResultStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RaftResultSerializationTest {

    @Test
    fun `should serialize and deserialize RaftResult with OK status`() {
        val result = RaftResult(
            version = 1,
            result = "success-data".toByteArray(),
            status = RaftResultStatus.OK
        )

        val serialized = serializeRaftResult(result)
        val deserialized = deserializeRaftResult(serialized)

        assertEquals(result, deserialized)
    }

    @Test
    fun `should serialize and deserialize RaftResult with NOT_FOUND status`() {
        val result = RaftResult(
            version = 1,
            result = ByteArray(0),
            status = RaftResultStatus.NOT_FOUND
        )

        val serialized = serializeRaftResult(result)
        val deserialized = deserializeRaftResult(serialized)

        assertEquals(result, deserialized)
    }

    @Test
    fun `should serialize and deserialize RaftResult with ERROR status`() {
        val result = RaftResult(
            version = 1,
            result = "error-message".toByteArray(),
            status = RaftResultStatus.ERROR
        )

        val serialized = serializeRaftResult(result)
        val deserialized = deserializeRaftResult(serialized)

        assertEquals(result.version, deserialized.version)
        assertEquals(result.status, deserialized.status)
        assertContentEquals(result.result, deserialized.result)
    }

    @Test
    fun `should serialize and deserialize RaftResult with empty result`() {
        val result = RaftResult(
            version = 1,
            result = ByteArray(0),
            status = RaftResultStatus.OK
        )

        val serialized = serializeRaftResult(result)
        val deserialized = deserializeRaftResult(serialized)

        assertEquals(result, deserialized)
    }

    @Test
    fun `should serialize and deserialize RaftResult with binary data`() {
        val result = RaftResult(
            version = 1,
            result = byteArrayOf(0, 1, 127, -128, -1, 42),
            status = RaftResultStatus.OK
        )

        val serialized = serializeRaftResult(result)
        val deserialized = deserializeRaftResult(serialized)

        assertEquals(result.version, deserialized.version)
        assertEquals(result.status, deserialized.status)
        assertContentEquals(result.result, deserialized.result)
    }

    @Test
    fun `should serialize and deserialize RaftResult with large result data`() {
        val largeData = ByteArray(100000) { it.toByte() }
        val result = RaftResult(
            version = 1,
            result = largeData,
            status = RaftResultStatus.OK
        )

        val serialized = serializeRaftResult(result)
        val deserialized = deserializeRaftResult(serialized)

        assertEquals(result.version, deserialized.version)
        assertEquals(result.status, deserialized.status)
        assertContentEquals(result.result, deserialized.result)
    }

    @Test
    fun `should serialize and deserialize RaftResult with UTF-8 data`() {
        val utf8Data = "Hello 世界 🌍 Привет مرحبا".toByteArray(Charsets.UTF_8)
        val result = RaftResult(
            version = 1,
            result = utf8Data,
            status = RaftResultStatus.OK
        )

        val serialized = serializeRaftResult(result)
        val deserialized = deserializeRaftResult(serialized)

        assertEquals(result.version, deserialized.version)
        assertEquals(result.status, deserialized.status)
        assertContentEquals(result.result, deserialized.result)
        assertEquals(
            "Hello 世界 🌍 Привет مرحبا",
            String(deserialized.result, Charsets.UTF_8)
        )
    }

    @Test
    fun `should serialize all RaftResultStatus values correctly`() {
        RaftResultStatus.entries.forEach { status ->
            val result = RaftResult(
                version = 1,
                result = "test-data-for-${status.name}".toByteArray(),
                status = status
            )

            val serialized = serializeRaftResult(result)
            val deserialized = deserializeRaftResult(serialized)

            assertEquals(
                status, deserialized.status,
                "Status $status not preserved after serialization"
            )
        }
    }

    @Test
    fun `should produce Base64-encoded string`() {
        val result = RaftResult(
            version = 1,
            result = "test-result".toByteArray(),
            status = RaftResultStatus.OK
        )

        val serialized = serializeRaftResult(result)

        // Base64 should only contain A-Z, a-z, 0-9, +, /, and = for padding
        assert(serialized.matches(Regex("^[A-Za-z0-9+/]+=*$"))) {
            "Serialized result should be valid Base64"
        }
    }

    @Test
    fun `should throw NotImplementedError for unsupported version during serialization`() {
        val result = RaftResult(
            version = 2,
            result = "test".toByteArray(),
            status = RaftResultStatus.OK
        )

        assertThrows<NotImplementedError> {
            serializeRaftResult(result)
        }
    }

    @Test
    fun `should throw IllegalStateException when serializing version 1 with wrong version`() {
        val result = RaftResult(
            version = 2,
            result = "test".toByteArray(),
            status = RaftResultStatus.OK
        )

        assertThrows<IllegalStateException> {
            serializeRaftResultV1(result)
        }
    }

    @Test
    fun `should serialize and deserialize RaftResult v1 directly`() {
        val result = RaftResult(
            version = 1,
            result = "direct-test".toByteArray(),
            status = RaftResultStatus.OK
        )

        val serialized = serializeRaftResultV1(result)
        val deserialized = deserializeRaftResult(serialized)

        assertEquals(result, deserialized)
    }

    @Test
    fun `should handle multiple round trips without data corruption`() {
        val result = RaftResult(
            version = 1,
            result = "test-result".toByteArray(),
            status = RaftResultStatus.OK
        )

        var serialized = serializeRaftResult(result)
        repeat(5) {
            val deserialized = deserializeRaftResult(serialized)
            serialized = serializeRaftResult(deserialized)
        }
        val final = deserializeRaftResult(serialized)

        assertEquals(result.version, final.version)
        assertEquals(result.status, final.status)
        assertContentEquals(result.result, final.result)
    }

    @Test
    fun `should serialize and deserialize RaftResult with JSON-like data`() {
        val jsonData = """{"key": "value", "number": 42, "nested": {"a": 1}}"""
        val result = RaftResult(
            version = 1,
            result = jsonData.toByteArray(),
            status = RaftResultStatus.OK
        )

        val serialized = serializeRaftResult(result)
        val deserialized = deserializeRaftResult(serialized)

        assertEquals(result.version, deserialized.version)
        assertEquals(result.status, deserialized.status)
        assertContentEquals(result.result, deserialized.result)
        assertEquals(jsonData, String(deserialized.result))
    }

    @Test
    fun `should differentiate between different statuses with same data`() {
        val data = "same-data".toByteArray()

        val resultOk = RaftResult(version = 1, result = data, status = RaftResultStatus.OK)
        val resultNotFound = RaftResult(version = 1, result = data, status = RaftResultStatus.NOT_FOUND)
        val resultError = RaftResult(version = 1, result = data, status = RaftResultStatus.ERROR)

        val serializedOk = serializeRaftResult(resultOk)
        val serializedNotFound = serializeRaftResult(resultNotFound)
        val serializedError = serializeRaftResult(resultError)

        val deserializedOk = deserializeRaftResult(serializedOk)
        val deserializedNotFound = deserializeRaftResult(serializedNotFound)
        val deserializedError = deserializeRaftResult(serializedError)

        assertEquals(RaftResultStatus.OK, deserializedOk.status)
        assertEquals(RaftResultStatus.NOT_FOUND, deserializedNotFound.status)
        assertEquals(RaftResultStatus.ERROR, deserializedError.status)
    }

    @Test
    fun `should handle error messages in result field`() {
        val errorMessage = "Error: Connection timeout after 30 seconds"
        val result = RaftResult(
            version = 1,
            result = errorMessage.toByteArray(),
            status = RaftResultStatus.ERROR
        )

        val serialized = serializeRaftResult(result)
        val deserialized = deserializeRaftResult(serialized)

        assertEquals(result.version, deserialized.version)
        assertEquals(result.status, deserialized.status)
        assertContentEquals(result.result, deserialized.result)
        assertEquals(errorMessage, String(deserialized.result))
    }
}
