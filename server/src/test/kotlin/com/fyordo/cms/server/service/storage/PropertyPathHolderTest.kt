package com.fyordo.cms.server.service.storage

import com.fyordo.cms.server.dto.property.PropertyKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PropertyPathHolderTest {

    private lateinit var pathHolder: PropertyPathHolder

    @BeforeEach
    fun setUp() {
        pathHolder = PropertyPathHolder()
    }

    @Test
    fun `should start with empty collections`() {
        assertTrue(pathHolder.getNamespaces().isEmpty())
        assertTrue(pathHolder.getServices().isEmpty())
        assertTrue(pathHolder.getAppIds().isEmpty())
        assertTrue(pathHolder.getKeys().isEmpty())
    }

    @Test
    fun `should add property and populate all collections`() {
        val key = PropertyKey(
            version = 1,
            namespace = "test-namespace",
            service = "test-service",
            appId = "test-app",
            key = "test-key"
        )

        pathHolder.addProperty(key)

        assertTrue(pathHolder.getNamespaces().contains("test-namespace"))
        assertTrue(pathHolder.getServices().contains("test-service"))
        assertTrue(pathHolder.getAppIds().contains("test-app"))
        assertTrue(pathHolder.getKeys().contains("test-key"))
    }

    @Test
    fun `should add multiple properties`() {
        val key1 = PropertyKey(
            version = 1,
            namespace = "ns1",
            service = "svc1",
            appId = "app1",
            key = "key1"
        )
        val key2 = PropertyKey(
            version = 1,
            namespace = "ns2",
            service = "svc2",
            appId = "app2",
            key = "key2"
        )

        pathHolder.addProperty(key1)
        pathHolder.addProperty(key2)

        assertEquals(2, pathHolder.getNamespaces().size)
        assertEquals(2, pathHolder.getServices().size)
        assertEquals(2, pathHolder.getAppIds().size)
        assertEquals(2, pathHolder.getKeys().size)
    }

    @Test
    fun `should not add duplicate values`() {
        val key1 = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "key1"
        )
        val key2 = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "key2"
        )

        pathHolder.addProperty(key1)
        pathHolder.addProperty(key2)

        // Same namespace, service, appId - should only have 1 each
        assertEquals(1, pathHolder.getNamespaces().size)
        assertEquals(1, pathHolder.getServices().size)
        assertEquals(1, pathHolder.getAppIds().size)
        // Different keys - should have 2
        assertEquals(2, pathHolder.getKeys().size)
    }

    @Test
    fun `should add same property multiple times without duplicates`() {
        val key = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "key"
        )

        pathHolder.addProperty(key)
        pathHolder.addProperty(key)
        pathHolder.addProperty(key)

        assertEquals(1, pathHolder.getNamespaces().size)
        assertEquals(1, pathHolder.getServices().size)
        assertEquals(1, pathHolder.getAppIds().size)
        assertEquals(1, pathHolder.getKeys().size)
    }

    @Test
    fun `should remove property key`() {
        val key = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "key"
        )

        pathHolder.addProperty(key)
        assertTrue(pathHolder.getKeys().contains("key"))

        pathHolder.removeProperty(key)
        assertFalse(pathHolder.getKeys().contains("key"))
    }

    @Test
    fun `should only remove key but keep other properties when removing`() {
        val key = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "key"
        )

        pathHolder.addProperty(key)
        pathHolder.removeProperty(key)

        // Key should be removed
        assertFalse(pathHolder.getKeys().contains("key"))
        // But namespace, service, appId should remain
        assertTrue(pathHolder.getNamespaces().contains("ns"))
        assertTrue(pathHolder.getServices().contains("svc"))
        assertTrue(pathHolder.getAppIds().contains("app"))
    }

    @Test
    fun `should handle removing non-existent key`() {
        val key = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "non-existent"
        )

        // Should not throw exception
        pathHolder.removeProperty(key)

        assertTrue(pathHolder.getKeys().isEmpty())
    }

    @Test
    fun `should handle empty string values`() {
        val key = PropertyKey(
            version = 1,
            namespace = "",
            service = "",
            appId = "",
            key = ""
        )

        pathHolder.addProperty(key)

        assertTrue(pathHolder.getNamespaces().contains(""))
        assertTrue(pathHolder.getServices().contains(""))
        assertTrue(pathHolder.getAppIds().contains(""))
        assertTrue(pathHolder.getKeys().contains(""))
    }

    @Test
    fun `should handle UTF-8 values`() {
        val key = PropertyKey(
            version = 1,
            namespace = "命名空间",
            service = "сервис",
            appId = "アプリ",
            key = "مفتاح"
        )

        pathHolder.addProperty(key)

        assertTrue(pathHolder.getNamespaces().contains("命名空间"))
        assertTrue(pathHolder.getServices().contains("сервис"))
        assertTrue(pathHolder.getAppIds().contains("アプリ"))
        assertTrue(pathHolder.getKeys().contains("مفتاح"))
    }

    @Test
    fun `should handle special characters`() {
        val key = PropertyKey(
            version = 1,
            namespace = "ns/with/slashes",
            service = "svc\\with\\backslashes",
            appId = "app:with:colons",
            key = "key.with.dots-and_underscores"
        )

        pathHolder.addProperty(key)

        assertTrue(pathHolder.getNamespaces().contains("ns/with/slashes"))
        assertTrue(pathHolder.getServices().contains("svc\\with\\backslashes"))
        assertTrue(pathHolder.getAppIds().contains("app:with:colons"))
        assertTrue(pathHolder.getKeys().contains("key.with.dots-and_underscores"))
    }

    @Test
    fun `should handle multiple keys with same namespace service and appId`() {
        val key1 = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "key1"
        )
        val key2 = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "key2"
        )
        val key3 = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "key3"
        )

        pathHolder.addProperty(key1)
        pathHolder.addProperty(key2)
        pathHolder.addProperty(key3)

        assertEquals(1, pathHolder.getNamespaces().size)
        assertEquals(1, pathHolder.getServices().size)
        assertEquals(1, pathHolder.getAppIds().size)
        assertEquals(3, pathHolder.getKeys().size)
    }

    @Test
    fun `should get mutable collections that can be modified`() {
        val key = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "key"
        )

        pathHolder.addProperty(key)

        val namespaces = pathHolder.getNamespaces()
        val services = pathHolder.getServices()
        val appIds = pathHolder.getAppIds()
        val keys = pathHolder.getKeys()

        // Collections should be mutable and contain added values
        assertTrue(namespaces is MutableSet)
        assertTrue(services is MutableSet)
        assertTrue(appIds is MutableSet)
        assertTrue(keys is MutableSet)
    }

    @Test
    fun `should handle large number of properties`() {
        repeat(1000) { i ->
            val key = PropertyKey(
                version = 1,
                namespace = "ns$i",
                service = "svc$i",
                appId = "app$i",
                key = "key$i"
            )
            pathHolder.addProperty(key)
        }

        assertEquals(1000, pathHolder.getNamespaces().size)
        assertEquals(1000, pathHolder.getServices().size)
        assertEquals(1000, pathHolder.getAppIds().size)
        assertEquals(1000, pathHolder.getKeys().size)
    }

    @Test
    fun `should handle removing multiple keys`() {
        val keys = listOf(
            PropertyKey(1, "ns", "svc", "app", "key1"),
            PropertyKey(1, "ns", "svc", "app", "key2"),
            PropertyKey(1, "ns", "svc", "app", "key3")
        )

        keys.forEach { pathHolder.addProperty(it) }
        assertEquals(3, pathHolder.getKeys().size)

        keys.forEach { pathHolder.removeProperty(it) }
        assertEquals(0, pathHolder.getKeys().size)
    }
}
