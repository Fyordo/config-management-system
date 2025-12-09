package com.fyordo.cms.server.helper

import com.fyordo.cms.server.dto.RaftCommand
import com.fyordo.cms.server.dto.RaftOp
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RaftCommandSerializationTest {

    @Test
    fun `should serialize and deserialize PUT command`() {
        val command = RaftCommand(
            operation = RaftOp.PUT,
            key = "testKey",
            value = "testValue"
        )
        
        val serialized = serializeCommand(command)
        val deserialized = deserializeCommand(serialized)
        
        assertEquals(command.operation, deserialized.operation)
        assertEquals(command.key, deserialized.key)
        assertEquals(command.value, deserialized.value)
        assertEquals(command.version, deserialized.version)
    }

    @Test
    fun `should serialize and deserialize GET command`() {
        val command = RaftCommand(
            operation = RaftOp.GET,
            key = "myKey",
            value = ""
        )

        val serialized = serializeCommand(command)
        val deserialized = deserializeCommand(serialized)

        assertEquals(command.operation, deserialized.operation)
        assertEquals(command.key, deserialized.key)
        assertEquals(command.value, deserialized.value)
    }

    @Test
    fun `should serialize and deserialize DELETE command`() {
        val command = RaftCommand(
            operation = RaftOp.DELETE,
            key = "deleteMe",
            value = ""
        )

        val serialized = serializeCommand(command)
        val deserialized = deserializeCommand(serialized)

        assertEquals(command.operation, deserialized.operation)
        assertEquals(command.key, deserialized.key)
        assertEquals(command.value, deserialized.value)
    }

    @Test
    fun `should handle special characters in key and value`() {
        val command = RaftCommand(
            operation = RaftOp.PUT,
            key = "key-with-дефис-и-кириллица",
            value = "value with spaces and специальные символы: !@#$%^&*()"
        )
        
        val serialized = serializeCommand(command)
        val deserialized = deserializeCommand(serialized)
        
        assertEquals(command.operation, deserialized.operation)
        assertEquals(command.key, deserialized.key)
        assertEquals(command.value, deserialized.value)
    }

    @Test
    fun `should handle empty strings`() {
        val command = RaftCommand(
            operation = RaftOp.PUT,
            key = "",
            value = ""
        )
        
        val serialized = serializeCommand(command)
        val deserialized = deserializeCommand(serialized)
        
        assertEquals(command.operation, deserialized.operation)
        assertEquals(command.key, deserialized.key)
        assertEquals(command.value, deserialized.value)
    }

    @Test
    fun `should handle long strings`() {
        val longValue = "a".repeat(10000)
        val command = RaftCommand(
            operation = RaftOp.PUT,
            key = "longKey",
            value = longValue
        )

        val serialized = serializeCommand(command)
        val deserialized = deserializeCommand(serialized)

        assertEquals(command.operation, deserialized.operation)
        assertEquals(command.key, deserialized.key)
        assertEquals(command.value, deserialized.value)
        assertEquals(longValue.length, deserialized.value.length)
    }

    @Test
    fun `serialized output should be Base64 encoded`() {
        val command = RaftCommand(
            operation = RaftOp.PUT,
            key = "test",
            value = "value"
        )

        val serialized = serializeCommand(command)

        assertTrue(serialized.matches(Regex("^[A-Za-z0-9+/]*={0,2}$")))
    }
}

