package com.fyordo.cms.server.serialization.property

import com.fyordo.cms.server.dto.property.PropertyKey
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class PropertyKeySerializationTest {

    @Test
    fun `should serialize and deserialize PropertyKey with simple values`() {
        val propertyKey = PropertyKey(
            version = 1,
            namespace = "test-namespace",
            service = "test-service",
            appId = "test-app",
            key = "test-key"
        )

        val serialized = serializePropertyKey(propertyKey)
        val deserialized = deserializePropertyKey(serialized)

        assertEquals(propertyKey, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyKey with empty strings`() {
        val propertyKey = PropertyKey(
            version = 1,
            namespace = "",
            service = "",
            appId = "",
            key = ""
        )

        val serialized = serializePropertyKey(propertyKey)
        val deserialized = deserializePropertyKey(serialized)

        assertEquals(propertyKey, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyKey with UTF-8 strings`() {
        val propertyKey = PropertyKey(
            version = 1,
            namespace = "пространство-имён",
            service = "服务",
            appId = "приложение",
            key = "ключ-🔑"
        )

        val serialized = serializePropertyKey(propertyKey)
        val deserialized = deserializePropertyKey(serialized)

        assertEquals(propertyKey, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyKey with long strings`() {
        val longString = "a".repeat(10000)
        val propertyKey = PropertyKey(
            version = 1,
            namespace = longString,
            service = longString,
            appId = longString,
            key = longString
        )

        val serialized = serializePropertyKey(propertyKey)
        val deserialized = deserializePropertyKey(serialized)

        assertEquals(propertyKey, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyKey with special characters`() {
        val propertyKey = PropertyKey(
            version = 1,
            namespace = "test/namespace",
            service = "test\\service",
            appId = "test:app",
            key = "test.key-with_special@chars#123"
        )

        val serialized = serializePropertyKey(propertyKey)
        val deserialized = deserializePropertyKey(serialized)

        assertEquals(propertyKey, deserialized)
    }

    @Test
    fun `should throw NotImplementedError for unsupported version during serialization`() {
        val propertyKey = PropertyKey(
            version = 2,
            namespace = "test-namespace",
            service = "test-service",
            appId = "test-app",
            key = "test-key"
        )

        assertThrows<NotImplementedError> {
            serializePropertyKey(propertyKey)
        }
    }

    @Test
    fun `should throw IllegalStateException when serializing version 1 with wrong version`() {
        val propertyKey = PropertyKey(
            version = 2,
            namespace = "test-namespace",
            service = "test-service",
            appId = "test-app",
            key = "test-key"
        )

        assertThrows<IllegalStateException> {
            serializePropertyKeyV1(propertyKey)
        }
    }

    @Test
    fun `should serialize and deserialize PropertyKey v1 directly`() {
        val propertyKey = PropertyKey(
            version = 1,
            namespace = "direct-namespace",
            service = "direct-service",
            appId = "direct-app",
            key = "direct-key"
        )

        val serialized = serializePropertyKeyV1(propertyKey)
        val deserialized = deserializePropertyKey(serialized)

        assertEquals(propertyKey, deserialized)
    }

    @Test
    fun `should preserve exact field values after round trip`() {
        val propertyKey = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "k"
        )

        val serialized = serializePropertyKey(propertyKey)
        val deserialized = deserializePropertyKey(serialized)

        assertEquals(propertyKey.version, deserialized.version)
        assertEquals(propertyKey.namespace, deserialized.namespace)
        assertEquals(propertyKey.service, deserialized.service)
        assertEquals(propertyKey.appId, deserialized.appId)
        assertEquals(propertyKey.key, deserialized.key)
    }

    @Test
    fun `should handle PropertyKey with whitespace`() {
        val propertyKey = PropertyKey(
            version = 1,
            namespace = "  namespace with spaces  ",
            service = "\tservice\t",
            appId = "app\nwith\nnewlines",
            key = "key with\ttabs"
        )

        val serialized = serializePropertyKey(propertyKey)
        val deserialized = deserializePropertyKey(serialized)

        assertEquals(propertyKey, deserialized)
    }
}
