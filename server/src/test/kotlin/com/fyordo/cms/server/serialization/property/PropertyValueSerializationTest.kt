package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.server.dto.property.PropertyValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PropertyValueSerializationTest {

    @Test
    fun `should serialize and deserialize PropertyValue with simple data`() {
        val propertyValue = PropertyValue(
            version = 1,
            value = "test-value".toByteArray(),
            lastModifiedMs = 1234567890L
        )

        val serialized = serializePropertyValue(propertyValue)
        val deserialized = deserializePropertyValue(serialized)

        assertEquals(propertyValue, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyValue with empty byte array`() {
        val propertyValue = PropertyValue(
            version = 1,
            value = ByteArray(0),
            lastModifiedMs = 0L
        )

        val serialized = serializePropertyValue(propertyValue)
        val deserialized = deserializePropertyValue(serialized)

        assertEquals(propertyValue, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyValue with binary data`() {
        val propertyValue = PropertyValue(
            version = 1,
            value = byteArrayOf(0, 1, 127, -128, -1, 42),
            lastModifiedMs = System.currentTimeMillis()
        )

        val serialized = serializePropertyValue(propertyValue)
        val deserialized = deserializePropertyValue(serialized)

        assertEquals(propertyValue.version, deserialized.version)
        assertEquals(propertyValue.lastModifiedMs, deserialized.lastModifiedMs)
        assertContentEquals(propertyValue.value, deserialized.value)
    }

    @Test
    fun `should serialize and deserialize PropertyValue with large byte array`() {
        val largeByteArray = ByteArray(100000) { it.toByte() }
        val propertyValue = PropertyValue(
            version = 1,
            value = largeByteArray,
            lastModifiedMs = Long.MAX_VALUE
        )

        val serialized = serializePropertyValue(propertyValue)
        val deserialized = deserializePropertyValue(serialized)

        assertEquals(propertyValue.version, deserialized.version)
        assertEquals(propertyValue.lastModifiedMs, deserialized.lastModifiedMs)
        assertContentEquals(propertyValue.value, deserialized.value)
    }

    @Test
    fun `should serialize and deserialize PropertyValue with minimum timestamp`() {
        val propertyValue = PropertyValue(
            version = 1,
            value = "value".toByteArray(),
            lastModifiedMs = Long.MIN_VALUE
        )

        val serialized = serializePropertyValue(propertyValue)
        val deserialized = deserializePropertyValue(serialized)

        assertEquals(propertyValue, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyValue with maximum timestamp`() {
        val propertyValue = PropertyValue(
            version = 1,
            value = "value".toByteArray(),
            lastModifiedMs = Long.MAX_VALUE
        )

        val serialized = serializePropertyValue(propertyValue)
        val deserialized = deserializePropertyValue(serialized)

        assertEquals(propertyValue, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyValue with UTF-8 encoded string`() {
        val utf8String = "Hello 世界 🌍 Привет"
        val propertyValue = PropertyValue(
            version = 1,
            value = utf8String.toByteArray(Charsets.UTF_8),
            lastModifiedMs = 1000L
        )

        val serialized = serializePropertyValue(propertyValue)
        val deserialized = deserializePropertyValue(serialized)

        assertEquals(propertyValue.version, deserialized.version)
        assertEquals(propertyValue.lastModifiedMs, deserialized.lastModifiedMs)
        assertContentEquals(propertyValue.value, deserialized.value)
        assertEquals(utf8String, String(deserialized.value, Charsets.UTF_8))
    }

    @Test
    fun `should throw NotImplementedError for unsupported version during serialization`() {
        val propertyValue = PropertyValue(
            version = 2,
            value = "test".toByteArray(),
            lastModifiedMs = 123L
        )

        assertThrows<NotImplementedError> {
            serializePropertyValue(propertyValue)
        }
    }

    @Test
    fun `should throw IllegalStateException when serializing version 1 with wrong version`() {
        val propertyValue = PropertyValue(
            version = 2,
            value = "test".toByteArray(),
            lastModifiedMs = 123L
        )

        assertThrows<IllegalStateException> {
            serializePropertyValueV1(propertyValue)
        }
    }

    @Test
    fun `should serialize and deserialize PropertyValue v1 directly`() {
        val propertyValue = PropertyValue(
            version = 1,
            value = "direct-test".toByteArray(),
            lastModifiedMs = 999L
        )

        val serialized = serializePropertyValueV1(propertyValue)
        val deserialized = deserializePropertyValue(serialized)

        assertEquals(propertyValue, deserialized)
    }

    @Test
    fun `should preserve timestamp precision after round trip`() {
        val timestamps = listOf(
            0L,
            1L,
            1234567890123L,
            System.currentTimeMillis(),
            Long.MAX_VALUE - 1,
            Long.MIN_VALUE + 1
        )

        timestamps.forEach { timestamp ->
            val propertyValue = PropertyValue(
                version = 1,
                value = "test".toByteArray(),
                lastModifiedMs = timestamp
            )

            val serialized = serializePropertyValue(propertyValue)
            val deserialized = deserializePropertyValue(serialized)

            assertEquals(
                timestamp, deserialized.lastModifiedMs,
                "Timestamp $timestamp not preserved after serialization"
            )
        }
    }

    @Test
    fun `should handle zero-length value with non-zero timestamp`() {
        val propertyValue = PropertyValue(
            version = 1,
            value = ByteArray(0),
            lastModifiedMs = 1234567890L
        )

        val serialized = serializePropertyValue(propertyValue)
        val deserialized = deserializePropertyValue(serialized)

        assertEquals(propertyValue, deserialized)
    }
}
