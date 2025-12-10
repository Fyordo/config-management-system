package com.fyordo.cms.server.service.storage

import com.fyordo.cms.server.dto.property.PropertyKey
import com.fyordo.cms.server.dto.property.PropertyValue
import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class PropertyInMemoryStorageTest {

    private lateinit var storage: PropertyInMemoryStorage
    private lateinit var pathHolder: PropertyPathHolder

    @BeforeEach
    fun setUp() {
        pathHolder = PropertyPathHolder()
        storage = PropertyInMemoryStorage(pathHolder)
    }

    @Test
    fun `should store and retrieve property`() {
        val key = PropertyKey(
            version = 1,
            namespace = "test-ns",
            service = "test-svc",
            appId = "test-app",
            key = "test-key"
        )
        val value = PropertyValue(
            version = 1,
            value = "test-value".toByteArray(),
            lastModifiedMs = 123456789L
        )

        storage[key] = value
        val retrieved = storage[key]

        assertEquals(value, retrieved)
    }

    @Test
    fun `should return null for non-existent key`() {
        val key = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "non-existent"
        )

        val retrieved = storage[key]

        assertNull(retrieved)
    }

    @Test
    fun `should update existing property`() {
        val key = PropertyKey(
            version = 1,
            namespace = "ns",
            service = "svc",
            appId = "app",
            key = "key"
        )
        val value1 = PropertyValue(
            version = 1,
            value = "value1".toByteArray(),
            lastModifiedMs = 100L
        )
        val value2 = PropertyValue(
            version = 1,
            value = "value2".toByteArray(),
            lastModifiedMs = 200L
        )

        storage[key] = value1
        storage[key] = value2
        val retrieved = storage[key]

        assertEquals(value2, retrieved)
        assertEquals("value2", String(retrieved!!.value))
    }

    @Test
    fun `should store multiple properties`() {
        val key1 = PropertyKey(1, "ns1", "svc1", "app1", "key1")
        val key2 = PropertyKey(1, "ns2", "svc2", "app2", "key2")
        val value1 = PropertyValue(1, "value1".toByteArray(), 100L)
        val value2 = PropertyValue(1, "value2".toByteArray(), 200L)

        storage[key1] = value1
        storage[key2] = value2

        assertEquals(value1, storage[key1])
        assertEquals(value2, storage[key2])
    }

    @Test
    fun `should remove property`() {
        val key = PropertyKey(1, "ns", "svc", "app", "key")
        val value = PropertyValue(1, "value".toByteArray(), 100L)

        storage[key] = value
        assertNotNull(storage[key])

        val removed = storage.remove(key)

        assertEquals(value, removed)
        assertNull(storage[key])
    }

    @Test
    fun `should return null when removing non-existent key`() {
        val key = PropertyKey(1, "ns", "svc", "app", "non-existent")

        val removed = storage.remove(key)

        assertNull(removed)
    }

    @Test
    fun `should update pathHolder when storing property`() {
        val key = PropertyKey(1, "ns", "svc", "app", "key")
        val value = PropertyValue(1, "value".toByteArray(), 100L)

        storage[key] = value

        assertTrue(pathHolder.getNamespaces().contains("ns"))
        assertTrue(pathHolder.getServices().contains("svc"))
        assertTrue(pathHolder.getAppIds().contains("app"))
        assertTrue(pathHolder.getKeys().contains("key"))
    }

    @Test
    fun `should update pathHolder when removing property`() {
        val key = PropertyKey(1, "ns", "svc", "app", "key")
        val value = PropertyValue(1, "value".toByteArray(), 100L)

        storage[key] = value
        assertTrue(pathHolder.getKeys().contains("key"))

        storage.remove(key)
        assertFalse(pathHolder.getKeys().contains("key"))
    }

    @Test
    fun `should filter by namespace regex`() {
        storage[PropertyKey(1, "prod", "svc", "app", "key1")] = PropertyValue(1, "v1".toByteArray(), 100L)
        storage[PropertyKey(1, "dev", "svc", "app", "key2")] = PropertyValue(1, "v2".toByteArray(), 100L)
        storage[PropertyKey(1, "test", "svc", "app", "key3")] = PropertyValue(1, "v3".toByteArray(), 100L)

        val filter = PropertyQueryFilter(
            namespaceRegex = "prod",
            limit = 10
        )

        val results = storage.getByFilter(filter).toList()

        assertEquals(1, results.size)
        assertEquals("prod", results[0].key.namespace)
    }

    @Test
    fun `should filter by service regex`() {
        storage[PropertyKey(1, "ns", "auth-service", "app", "key1")] = PropertyValue(1, "v1".toByteArray(), 100L)
        storage[PropertyKey(1, "ns", "user-service", "app", "key2")] = PropertyValue(1, "v2".toByteArray(), 100L)
        storage[PropertyKey(1, "ns", "payment-service", "app", "key3")] = PropertyValue(1, "v3".toByteArray(), 100L)

        val filter = PropertyQueryFilter(
            serviceRegex = ".*-service",
            limit = 10
        )

        val results = storage.getByFilter(filter).toList()

        assertEquals(3, results.size)
    }

    @Test
    fun `should filter by appId regex`() {
        storage[PropertyKey(1, "ns", "svc", "app-1", "key1")] = PropertyValue(1, "v1".toByteArray(), 100L)
        storage[PropertyKey(1, "ns", "svc", "app-2", "key2")] = PropertyValue(1, "v2".toByteArray(), 100L)
        storage[PropertyKey(1, "ns", "svc", "other", "key3")] = PropertyValue(1, "v3".toByteArray(), 100L)

        val filter = PropertyQueryFilter(
            appIdRegex = "app-.*",
            limit = 10
        )

        val results = storage.getByFilter(filter).toList()

        assertEquals(2, results.size)
        assertTrue(results.all { it.key.appId.startsWith("app-") })
    }

    @Test
    fun `should filter by key regex`() {
        storage[PropertyKey(1, "ns", "svc", "app", "config.db.host")] = PropertyValue(1, "v1".toByteArray(), 100L)
        storage[PropertyKey(1, "ns", "svc", "app", "config.db.port")] = PropertyValue(1, "v2".toByteArray(), 100L)
        storage[PropertyKey(1, "ns", "svc", "app", "feature.flag")] = PropertyValue(1, "v3".toByteArray(), 100L)

        val filter = PropertyQueryFilter(
            keyRegex = "config\\..*",
            limit = 10
        )

        val results = storage.getByFilter(filter).toList()

        assertEquals(2, results.size)
        assertTrue(results.all { it.key.key.startsWith("config.") })
    }

    @Test
    fun `should filter by multiple criteria`() {
        storage[PropertyKey(1, "prod", "auth-service", "app1", "key1")] = PropertyValue(1, "v1".toByteArray(), 100L)
        storage[PropertyKey(1, "prod", "user-service", "app1", "key2")] = PropertyValue(1, "v2".toByteArray(), 100L)
        storage[PropertyKey(1, "dev", "auth-service", "app1", "key3")] = PropertyValue(1, "v3".toByteArray(), 100L)
        storage[PropertyKey(1, "prod", "auth-service", "app2", "key4")] = PropertyValue(1, "v4".toByteArray(), 100L)

        val filter = PropertyQueryFilter(
            namespaceRegex = "prod",
            serviceRegex = "auth-service",
            appIdRegex = "app1",
            limit = 10
        )

        val results = storage.getByFilter(filter).toList()

        assertEquals(1, results.size)
        assertEquals("prod", results[0].key.namespace)
        assertEquals("auth-service", results[0].key.service)
        assertEquals("app1", results[0].key.appId)
    }

    @Test
    fun `should respect limit in filter`() {
        repeat(10) { i ->
            storage[PropertyKey(1, "ns", "svc", "app", "key$i")] = PropertyValue(1, "v$i".toByteArray(), 100L)
        }

        val filter = PropertyQueryFilter(
            namespaceRegex = "ns",
            limit = 5
        )

        val results = storage.getByFilter(filter).toList()

        assertEquals(5, results.size)
    }

    @Test
    fun `should return empty sequence when no matches found`() {
        storage[PropertyKey(1, "ns1", "svc1", "app1", "key1")] = PropertyValue(1, "v1".toByteArray(), 100L)

        val filter = PropertyQueryFilter(
            namespaceRegex = "non-existent",
            limit = 10
        )

        val results = storage.getByFilter(filter).toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `should return all properties when filter has no regex patterns`() {
        storage[PropertyKey(1, "ns1", "svc1", "app1", "key1")] = PropertyValue(1, "v1".toByteArray(), 100L)
        storage[PropertyKey(1, "ns2", "svc2", "app2", "key2")] = PropertyValue(1, "v2".toByteArray(), 100L)
        storage[PropertyKey(1, "ns3", "svc3", "app3", "key3")] = PropertyValue(1, "v3".toByteArray(), 100L)

        val filter = PropertyQueryFilter(
            namespaceRegex = null,
            serviceRegex = null,
            appIdRegex = null,
            keyRegex = null,
            limit = 10
        )

        val results = storage.getByFilter(filter).toList()

        assertEquals(3, results.size)
    }

    @Test
    fun `should handle empty byte array values`() {
        val key = PropertyKey(1, "ns", "svc", "app", "key")
        val value = PropertyValue(1, ByteArray(0), 100L)

        storage[key] = value
        val retrieved = storage[key]

        assertNotNull(retrieved)
        assertContentEquals(ByteArray(0), retrieved.value)
    }

    @Test
    fun `should handle large byte array values`() {
        val key = PropertyKey(1, "ns", "svc", "app", "key")
        val largeArray = ByteArray(100000) { it.toByte() }
        val value = PropertyValue(1, largeArray, 100L)

        storage[key] = value
        val retrieved = storage[key]

        assertNotNull(retrieved)
        assertContentEquals(largeArray, retrieved.value)
    }

    @Test
    fun `should handle UTF-8 values in keys`() {
        val key = PropertyKey(1, "命名空间", "сервис", "アプリ", "مفتاح")
        val value = PropertyValue(1, "значение 🎉".toByteArray(Charsets.UTF_8), 100L)

        storage[key] = value
        val retrieved = storage[key]

        assertNotNull(retrieved)
        assertEquals("значение 🎉", String(retrieved.value, Charsets.UTF_8))
    }

    @Test
    fun `should handle special characters in keys`() {
        val key = PropertyKey(1, "ns/with/slashes", "svc\\with\\backslashes", "app:with:colons", "key.with.dots")
        val value = PropertyValue(1, "value".toByteArray(), 100L)

        storage[key] = value
        val retrieved = storage[key]

        assertNotNull(retrieved)
        assertEquals(value, retrieved)
    }

    @Test
    fun `should handle concurrent-like operations`() {
        val keys = (1..100).map { i ->
            PropertyKey(1, "ns$i", "svc$i", "app$i", "key$i")
        }
        val values = (1..100).map { i ->
            PropertyValue(1, "value$i".toByteArray(), i.toLong())
        }

        keys.zip(values).forEach { (key, value) ->
            storage[key] = value
        }

        keys.zip(values).forEach { (key, value) ->
            assertEquals(value, storage[key])
        }
    }

    @Test
    fun `should handle timestamp edge cases`() {
        val key1 = PropertyKey(1, "ns", "svc", "app", "key1")
        val key2 = PropertyKey(1, "ns", "svc", "app", "key2")
        val key3 = PropertyKey(1, "ns", "svc", "app", "key3")

        val value1 = PropertyValue(1, "v1".toByteArray(), Long.MIN_VALUE)
        val value2 = PropertyValue(1, "v2".toByteArray(), 0L)
        val value3 = PropertyValue(1, "v3".toByteArray(), Long.MAX_VALUE)

        storage[key1] = value1
        storage[key2] = value2
        storage[key3] = value3

        assertEquals(Long.MIN_VALUE, storage[key1]?.lastModifiedMs)
        assertEquals(0L, storage[key2]?.lastModifiedMs)
        assertEquals(Long.MAX_VALUE, storage[key3]?.lastModifiedMs)
    }

    @Test
    fun `should handle complex regex patterns in filter`() {
        storage[PropertyKey(1, "prod-eu-west-1", "svc", "app", "key1")] = PropertyValue(1, "v1".toByteArray(), 100L)
        storage[PropertyKey(1, "prod-us-east-1", "svc", "app", "key2")] = PropertyValue(1, "v2".toByteArray(), 100L)
        storage[PropertyKey(1, "dev-eu-west-1", "svc", "app", "key3")] = PropertyValue(1, "v3".toByteArray(), 100L)

        val filter = PropertyQueryFilter(
            namespaceRegex = "^prod-.*-1$",
            limit = 10
        )

        val results = storage.getByFilter(filter).toList()

        assertEquals(2, results.size)
        assertTrue(results.all { it.key.namespace.startsWith("prod-") && it.key.namespace.endsWith("-1") })
    }

    @Test
    fun `should return results as sequence for lazy evaluation`() {
        repeat(1000) { i ->
            storage[PropertyKey(1, "ns", "svc", "app", "key$i")] = PropertyValue(1, "v$i".toByteArray(), 100L)
        }

        val filter = PropertyQueryFilter(
            namespaceRegex = "ns",
            limit = 10
        )

        val results = storage.getByFilter(filter)

        // Verify it's a Sequence (not evaluated yet)
        assertTrue(results is Sequence)

        // Take only first 5 - should not evaluate all 1000
        val firstFive = results.take(5).toList()
        assertEquals(5, firstFive.size)
    }

    @Test
    fun `should handle removing and re-adding same key`() {
        val key = PropertyKey(1, "ns", "svc", "app", "key")
        val value1 = PropertyValue(1, "value1".toByteArray(), 100L)
        val value2 = PropertyValue(1, "value2".toByteArray(), 200L)

        storage[key] = value1
        assertEquals(value1, storage[key])

        storage.remove(key)
        assertNull(storage[key])

        storage[key] = value2
        assertEquals(value2, storage[key])
    }

    @Test
    fun `should handle zero limit in filter`() {
        storage[PropertyKey(1, "ns", "svc", "app", "key1")] = PropertyValue(1, "v1".toByteArray(), 100L)
        storage[PropertyKey(1, "ns", "svc", "app", "key2")] = PropertyValue(1, "v2".toByteArray(), 100L)

        val filter = PropertyQueryFilter(
            namespaceRegex = "ns",
            limit = 0
        )

        val results = storage.getByFilter(filter).toList()

        assertEquals(0, results.size)
    }
}
