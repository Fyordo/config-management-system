package com.fyordo.cms.server.serialization

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CommonSerializationTest {

    @Test
    fun `should serialize and deserialize empty list`() {
        val emptyList = emptyList<String>()

        val serialized = serializeList(emptyList) { it.toByteArray() }
        val deserialized = deserializeList(serialized) { String(it) }

        assertEquals(emptyList, deserialized)
    }

    @Test
    fun `should serialize and deserialize list with single element`() {
        val list = listOf("test")

        val serialized = serializeList(list) { it.toByteArray() }
        val deserialized = deserializeList(serialized) { String(it) }

        assertEquals(list, deserialized)
    }

    @Test
    fun `should serialize and deserialize list with multiple elements`() {
        val list = listOf("first", "second", "third", "fourth")

        val serialized = serializeList(list) { it.toByteArray() }
        val deserialized = deserializeList(serialized) { String(it) }

        assertEquals(list, deserialized)
    }

    @Test
    fun `should serialize and deserialize list with empty strings`() {
        val list = listOf("", "test", "", "")

        val serialized = serializeList(list) { it.toByteArray() }
        val deserialized = deserializeList(serialized) { String(it) }

        assertEquals(list, deserialized)
    }

    @Test
    fun `should serialize and deserialize list with UTF-8 strings`() {
        val list = listOf("Hello", "Привет", "你好", "مرحبا", "🎉")

        val serialized = serializeList(list) { it.toByteArray() }
        val deserialized = deserializeList(serialized) { String(it) }

        assertEquals(list, deserialized)
    }

    @Test
    fun `should serialize and deserialize list with long strings`() {
        val longString = "a".repeat(10000)
        val list = listOf(longString, "short", longString)

        val serialized = serializeList(list) { it.toByteArray() }
        val deserialized = deserializeList(serialized) { String(it) }

        assertEquals(list, deserialized)
    }

    @Test
    fun `should serialize and deserialize list with byte arrays`() {
        val list = listOf(
            byteArrayOf(1, 2, 3),
            byteArrayOf(),
            byteArrayOf(127, -128, 0)
        )

        val serialized = serializeList(list) { it }
        val deserialized = deserializeList(serialized) { it }

        assertEquals(list.size, deserialized.size)
        list.zip(deserialized).forEach { (expected, actual) ->
            assertContentEquals(expected, actual)
        }
    }

    @Test
    fun `should serialize and deserialize list with integers`() {
        val list = listOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE, 42)

        val serialized = serializeList(list) {
            byteArrayOf(
                (it shr 24).toByte(),
                (it shr 16).toByte(),
                (it shr 8).toByte(),
                it.toByte()
            )
        }
        val deserialized = deserializeList(serialized) { bytes ->
            (bytes[0].toInt() and 0xFF shl 24) or
                    (bytes[1].toInt() and 0xFF shl 16) or
                    (bytes[2].toInt() and 0xFF shl 8) or
                    (bytes[3].toInt() and 0xFF)
        }

        assertEquals(list, deserialized)
    }

    @Test
    fun `should handle large list`() {
        val list = (1..1000).map { "item_$it" }

        val serialized = serializeList(list) { it.toByteArray() }
        val deserialized = deserializeList(serialized) { String(it) }

        assertEquals(list, deserialized)
    }
}
