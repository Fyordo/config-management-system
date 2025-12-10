package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.server.dto.property.PropertyInternalDto
import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PropertyFullDtoSerializationTest {

    @Test
    fun `should serialize and deserialize PropertyInternalDto with simple data`() {
        val dto = PropertyInternalDto(
            key = PropertyKey(
                version = 1,
                namespace = "test-namespace",
                service = "test-service",
                appId = "test-app",
                key = "test-key"
            ),
            value = PropertyValue(
                version = 1,
                value = "test-value".toByteArray(),
                lastModifiedMs = 1234567890L
            )
        )

        val serialized = serializePropertyInternalDto(dto)
        val deserialized = deserializePropertyInternalDto(serialized)

        assertEquals(dto.key, deserialized.key)
        assertEquals(dto.value.version, deserialized.value.version)
        assertEquals(dto.value.lastModifiedMs, deserialized.value.lastModifiedMs)
        assertContentEquals(dto.value.value, deserialized.value.value)
    }

    @Test
    fun `should serialize and deserialize PropertyInternalDto with empty values`() {
        val dto = PropertyInternalDto(
            key = PropertyKey(
                version = 1,
                namespace = "",
                service = "",
                appId = "",
                key = ""
            ),
            value = PropertyValue(
                version = 1,
                value = ByteArray(0),
                lastModifiedMs = 0L
            )
        )

        val serialized = serializePropertyInternalDto(dto)
        val deserialized = deserializePropertyInternalDto(serialized)

        assertEquals(dto.key, deserialized.key)
        assertEquals(dto.value, deserialized.value)
    }

    @Test
    fun `should serialize and deserialize PropertyInternalDto with UTF-8 data`() {
        val dto = PropertyInternalDto(
            key = PropertyKey(
                version = 1,
                namespace = "命名空间",
                service = "сервис",
                appId = "アプリ",
                key = "مفتاح"
            ),
            value = PropertyValue(
                version = 1,
                value = "значение 🎉".toByteArray(Charsets.UTF_8),
                lastModifiedMs = System.currentTimeMillis()
            )
        )

        val serialized = serializePropertyInternalDto(dto)
        val deserialized = deserializePropertyInternalDto(serialized)

        assertEquals(dto.key, deserialized.key)
        assertEquals(dto.value.version, deserialized.value.version)
        assertEquals(dto.value.lastModifiedMs, deserialized.value.lastModifiedMs)
        assertContentEquals(dto.value.value, deserialized.value.value)
    }

    @Test
    fun `should serialize and deserialize PropertyInternalDto with large data`() {
        val largeString = "a".repeat(50000)
        val largeByteArray = ByteArray(50000) { it.toByte() }

        val dto = PropertyInternalDto(
            key = PropertyKey(
                version = 1,
                namespace = largeString,
                service = largeString,
                appId = largeString,
                key = largeString
            ),
            value = PropertyValue(
                version = 1,
                value = largeByteArray,
                lastModifiedMs = Long.MAX_VALUE
            )
        )

        val serialized = serializePropertyInternalDto(dto)
        val deserialized = deserializePropertyInternalDto(serialized)

        assertEquals(dto.key, deserialized.key)
        assertEquals(dto.value.version, deserialized.value.version)
        assertEquals(dto.value.lastModifiedMs, deserialized.value.lastModifiedMs)
        assertContentEquals(dto.value.value, deserialized.value.value)
    }

    @Test
    fun `should serialize and deserialize PropertyInternalDto with binary value`() {
        val dto = PropertyInternalDto(
            key = PropertyKey(
                version = 1,
                namespace = "binary-test",
                service = "test-service",
                appId = "test-app",
                key = "binary-key"
            ),
            value = PropertyValue(
                version = 1,
                value = byteArrayOf(0, 1, 127, -128, -1, 42, 100),
                lastModifiedMs = 999999999L
            )
        )

        val serialized = serializePropertyInternalDto(dto)
        val deserialized = deserializePropertyInternalDto(serialized)

        assertEquals(dto.key, deserialized.key)
        assertEquals(dto.value.version, deserialized.value.version)
        assertEquals(dto.value.lastModifiedMs, deserialized.value.lastModifiedMs)
        assertContentEquals(dto.value.value, deserialized.value.value)
    }

    @Test
    fun `should serialize and deserialize PropertyInternalDto with extreme timestamps`() {
        val dtoMin = PropertyInternalDto(
            key = PropertyKey(
                version = 1,
                namespace = "ns",
                service = "svc",
                appId = "app",
                key = "key"
            ),
            value = PropertyValue(
                version = 1,
                value = "test".toByteArray(),
                lastModifiedMs = Long.MIN_VALUE
            )
        )

        val serializedMin = serializePropertyInternalDto(dtoMin)
        val deserializedMin = deserializePropertyInternalDto(serializedMin)
        assertEquals(Long.MIN_VALUE, deserializedMin.value.lastModifiedMs)

        val dtoMax = PropertyInternalDto(
            key = PropertyKey(
                version = 1,
                namespace = "ns",
                service = "svc",
                appId = "app",
                key = "key"
            ),
            value = PropertyValue(
                version = 1,
                value = "test".toByteArray(),
                lastModifiedMs = Long.MAX_VALUE
            )
        )

        val serializedMax = serializePropertyInternalDto(dtoMax)
        val deserializedMax = deserializePropertyInternalDto(serializedMax)
        assertEquals(Long.MAX_VALUE, deserializedMax.value.lastModifiedMs)
    }

    @Test
    fun `should serialize and deserialize PropertyInternalDto with special characters in key`() {
        val dto = PropertyInternalDto(
            key = PropertyKey(
                version = 1,
                namespace = "ns/with/slashes",
                service = "svc\\with\\backslashes",
                appId = "app:with:colons",
                key = "key.with.dots-and_underscores@symbols#123"
            ),
            value = PropertyValue(
                version = 1,
                value = "value with\ttabs\nand\nnewlines".toByteArray(),
                lastModifiedMs = 1234567890123L
            )
        )

        val serialized = serializePropertyInternalDto(dto)
        val deserialized = deserializePropertyInternalDto(serialized)

        assertEquals(dto.key, deserialized.key)
        assertEquals(dto.value.version, deserialized.value.version)
        assertEquals(dto.value.lastModifiedMs, deserialized.value.lastModifiedMs)
        assertContentEquals(dto.value.value, deserialized.value.value)
    }

    @Test
    fun `should handle multiple round trips without data corruption`() {
        val dto = PropertyInternalDto(
            key = PropertyKey(
                version = 1,
                namespace = "test-namespace",
                service = "test-service",
                appId = "test-app",
                key = "test-key"
            ),
            value = PropertyValue(
                version = 1,
                value = "test-value".toByteArray(),
                lastModifiedMs = 1234567890L
            )
        )

        var serialized = serializePropertyInternalDto(dto)
        repeat(5) {
            val deserialized = deserializePropertyInternalDto(serialized)
            serialized = serializePropertyInternalDto(deserialized)
        }
        val final = deserializePropertyInternalDto(serialized)

        assertEquals(dto.key, final.key)
        assertEquals(dto.value.version, final.value.version)
        assertEquals(dto.value.lastModifiedMs, final.value.lastModifiedMs)
        assertContentEquals(dto.value.value, final.value.value)
    }
}
