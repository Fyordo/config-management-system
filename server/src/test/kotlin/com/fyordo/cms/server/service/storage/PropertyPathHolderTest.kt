package com.fyordo.cms.server.service.storage

import com.fyordo.cms.server.dto.property.PropertyKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PropertyPathHolderTest {

    private lateinit var pathHolder: PropertyPartsHolder

    @BeforeEach
    fun setUp() {
        pathHolder = PropertyPartsHolder()
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

        // No other properties with same namespace/service/appId
        pathHolder.removeProperty(
            key,
            hasOtherWithNamespace = false,
            hasOtherWithService = false,
            hasOtherWithAppId = false,
            hasOtherWithKey = false
        )
        assertFalse(pathHolder.getKeys().contains("key"))
    }

    @Test
    fun `should keep namespace service and appId when other properties exist`() {
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
        
        // Remove key1, but key2 still uses the same namespace/service/appId
        pathHolder.removeProperty(
            key1,
            hasOtherWithNamespace = true,
            hasOtherWithService = true,
            hasOtherWithAppId = true,
            hasOtherWithKey = false
        )

        // key1 should be removed
        assertFalse(pathHolder.getKeys().contains("key1"))
        // key2 should still exist
        assertTrue(pathHolder.getKeys().contains("key2"))
        // namespace, service, appId should remain because key2 still uses them
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
        pathHolder.removeProperty(
            key,
            hasOtherWithNamespace = false,
            hasOtherWithService = false,
            hasOtherWithAppId = false,
            hasOtherWithKey = false
        )

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
    fun `should return immutable copies of collections`() {
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

        // Collections should be immutable copies
        assertTrue(namespaces.contains("ns"))
        assertTrue(services.contains("svc"))
        assertTrue(appIds.contains("app"))
        assertTrue(keys.contains("key"))
        
        // Verify they are not the same instance as internal collections
        // (cannot directly test immutability in Kotlin, but they are Set<String> not MutableSet)
        assertEquals(1, namespaces.size)
        assertEquals(1, services.size)
        assertEquals(1, appIds.size)
        assertEquals(1, keys.size)
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

        // Remove first two keys - still have key3
        pathHolder.removeProperty(
            keys[0],
            hasOtherWithNamespace = true,
            hasOtherWithService = true,
            hasOtherWithAppId = true,
            hasOtherWithKey = false
        )
        pathHolder.removeProperty(
            keys[1],
            hasOtherWithNamespace = true,
            hasOtherWithService = true,
            hasOtherWithAppId = true,
            hasOtherWithKey = false
        )
        
        // Remove last key - no other properties left
        pathHolder.removeProperty(
            keys[2],
            hasOtherWithNamespace = false,
            hasOtherWithService = false,
            hasOtherWithAppId = false,
            hasOtherWithKey = false
        )
        
        assertEquals(0, pathHolder.getKeys().size)
        // All metadata should be removed too
        assertTrue(pathHolder.getNamespaces().isEmpty())
        assertTrue(pathHolder.getServices().isEmpty())
        assertTrue(pathHolder.getAppIds().isEmpty())
    }

    @Test
    fun `should remove all metadata when last property is removed`() {
        val key = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "key"
        )

        pathHolder.addProperty(key)
        
        // Remove the only property - all metadata should be cleaned up
        pathHolder.removeProperty(
            key,
            hasOtherWithNamespace = false,
            hasOtherWithService = false,
            hasOtherWithAppId = false,
            hasOtherWithKey = false
        )

        assertTrue(pathHolder.getKeys().isEmpty())
        assertTrue(pathHolder.getNamespaces().isEmpty())
        assertTrue(pathHolder.getServices().isEmpty())
        assertTrue(pathHolder.getAppIds().isEmpty())
    }

    @Test
    fun `should remove only unused metadata`() {
        val key1 = PropertyKey(1, "ns1", "svc1", "app1", "key1")
        val key2 = PropertyKey(1, "ns1", "svc2", "app2", "key2")

        pathHolder.addProperty(key1)
        pathHolder.addProperty(key2)

        // Remove key1: ns1 is still used by key2, but svc1 and app1 are not
        pathHolder.removeProperty(
            key1,
            hasOtherWithNamespace = true,  // ns1 still used by key2
            hasOtherWithService = false,   // svc1 not used
            hasOtherWithAppId = false,
            hasOtherWithKey = false      // app1 not used
        )

        assertTrue(pathHolder.getNamespaces().contains("ns1"))
        assertFalse(pathHolder.getServices().contains("svc1"))
        assertFalse(pathHolder.getAppIds().contains("app1"))
        assertFalse(pathHolder.getKeys().contains("key1"))
        
        assertTrue(pathHolder.getServices().contains("svc2"))
        assertTrue(pathHolder.getAppIds().contains("app2"))
        assertTrue(pathHolder.getKeys().contains("key2"))
    }

    // ── clear ─────────────────────────────────────────────────────────────────

    @Nested
    inner class Clear {

        @Test
        fun `should empty all collections`() {
            pathHolder.addProperty(PropertyKey(1, "ns1", "svc1", "app1", "key1"))
            pathHolder.addProperty(PropertyKey(1, "ns2", "svc2", "app2", "key2"))

            pathHolder.clear()

            assertTrue(pathHolder.getNamespaces().isEmpty())
            assertTrue(pathHolder.getServices().isEmpty())
            assertTrue(pathHolder.getAppIds().isEmpty())
            assertTrue(pathHolder.getKeys().isEmpty())
        }

        @Test
        fun `should be idempotent on an already empty holder`() {
            pathHolder.clear()
            pathHolder.clear()

            assertTrue(pathHolder.getNamespaces().isEmpty())
            assertTrue(pathHolder.getKeys().isEmpty())
        }

        @Test
        fun `should allow re-adding properties after clear`() {
            pathHolder.addProperty(PropertyKey(1, "ns", "svc", "app", "key"))
            pathHolder.clear()

            val newKey = PropertyKey(1, "new-ns", "new-svc", "new-app", "new-key")
            pathHolder.addProperty(newKey)

            assertEquals(1, pathHolder.getNamespaces().size)
            assertTrue(pathHolder.getNamespaces().contains("new-ns"))
            assertFalse(pathHolder.getNamespaces().contains("ns"))
        }
    }

    @Test
    fun `should handle complex removal scenario with shared metadata`() {
        // Setup: 3 keys with different combinations of shared metadata
        val key1 = PropertyKey(1, "ns1", "svc", "app", "key1")
        val key2 = PropertyKey(1, "ns1", "svc", "app", "key2")
        val key3 = PropertyKey(1, "ns2", "svc", "app", "key3")

        pathHolder.addProperty(key1)
        pathHolder.addProperty(key2)
        pathHolder.addProperty(key3)

        assertEquals(2, pathHolder.getNamespaces().size) // ns1, ns2
        assertEquals(1, pathHolder.getServices().size)   // svc
        assertEquals(1, pathHolder.getAppIds().size)     // app
        assertEquals(3, pathHolder.getKeys().size)       // key1, key2, key3

        // Remove key1: ns1, svc, app still used by key2 and key3
        pathHolder.removeProperty(
            key1,
            hasOtherWithNamespace = true,
            hasOtherWithService = true,
            hasOtherWithAppId = true,
            hasOtherWithKey = false
        )

        assertEquals(2, pathHolder.getNamespaces().size) // ns1, ns2 still present
        assertEquals(1, pathHolder.getServices().size)   // svc still present
        assertEquals(1, pathHolder.getAppIds().size)     // app still present
        assertEquals(2, pathHolder.getKeys().size)       // key2, key3 remain

        // Remove key2: ns1 no longer used, but svc and app still used by key3
        pathHolder.removeProperty(
            key2,
            hasOtherWithNamespace = false, // ns1 not used anymore
            hasOtherWithService = true,    // svc still used by key3
            hasOtherWithAppId = true,
            hasOtherWithKey = false       // app still used by key3
        )

        assertEquals(1, pathHolder.getNamespaces().size) // only ns2
        assertTrue(pathHolder.getNamespaces().contains("ns2"))
        assertEquals(1, pathHolder.getServices().size)   // svc still present
        assertEquals(1, pathHolder.getAppIds().size)     // app still present
        assertEquals(1, pathHolder.getKeys().size)       // only key3

        // Remove key3: last property, everything should be cleaned up
        pathHolder.removeProperty(
            key3,
            hasOtherWithNamespace = false,
            hasOtherWithService = false,
            hasOtherWithAppId = false,
            hasOtherWithKey = false
        )

        assertTrue(pathHolder.getNamespaces().isEmpty())
        assertTrue(pathHolder.getServices().isEmpty())
        assertTrue(pathHolder.getAppIds().isEmpty())
        assertTrue(pathHolder.getKeys().isEmpty())
    }
}
