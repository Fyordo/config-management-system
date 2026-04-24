package com.fyordo.cms.server.service.storage

import com.fyordo.cms.CmsProto
import com.google.protobuf.ByteString
import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import com.fyordo.cms.server.utils.EMPTY_BYTES
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.*

class PropertyInMemoryStorageTest {
    private fun PropertyKey(
        version: Int,
        namespace: String,
        service: String,
        appId: String,
        key: String
    ): CmsProto.PropertyKey = CmsProto.PropertyKey.newBuilder()
        .setVersion(version)
        .setNamespace(namespace)
        .setService(service)
        .setAppId(appId)
        .setKey(key)
        .build()

    private fun PropertyValue(
        version: Int,
        value: ByteArray,
        lastModifiedMs: Long
    ): CmsProto.PropertyValue = CmsProto.PropertyValue.newBuilder()
        .setVersion(version)
        .setValue(ByteString.copyFrom(value))
        .setLastModifiedMs(lastModifiedMs)
        .build()

    private fun PropertyInternalDto(
        key: CmsProto.PropertyKey,
        value: CmsProto.PropertyValue
    ): CmsProto.PropertyInternalDto = CmsProto.PropertyInternalDto.newBuilder()
        .setKey(key)
        .setValue(value)
        .build()

    private lateinit var storage: PropertyInMemoryStorage
    private lateinit var pathHolder: PropertyPartsHolder

    @BeforeEach
    fun setUp() {
        pathHolder = PropertyPartsHolder()
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

        storage.setWithRevision(key, value, 1)
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

        storage.setWithRevision(key, value1, 1)
        storage.setWithRevision(key, value2, 2)
        val retrieved = storage[key]

        assertEquals(value2, retrieved)
        assertEquals("value2", String(retrieved!!.value.toByteArray()))
    }

    @Test
    fun `should store multiple properties`() {
        val key1 = PropertyKey(1, "ns1", "svc1", "app1", "key1")
        val key2 = PropertyKey(1, "ns2", "svc2", "app2", "key2")
        val value1 = PropertyValue(1, "value1".toByteArray(), 100L)
        val value2 = PropertyValue(1, "value2".toByteArray(), 200L)

        storage.setWithRevision(key1, value1, 1)
        storage.setWithRevision(key2, value2, 2)

        assertEquals(value1, storage[key1])
        assertEquals(value2, storage[key2])
    }

    @Test
    fun `should remove property`() {
        val key = PropertyKey(1, "ns", "svc", "app", "key")
        val value = PropertyValue(1, "value".toByteArray(), 100L)

        storage.setWithRevision(key, value, 1)
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

        storage.setWithRevision(key, value, 1)

        assertTrue(pathHolder.getNamespaces().contains("ns"))
        assertTrue(pathHolder.getServices().contains("svc"))
        assertTrue(pathHolder.getAppIds().contains("app"))
        assertTrue(pathHolder.getKeys().contains("key"))
    }

    @Test
    fun `should update pathHolder when removing property`() {
        val key = PropertyKey(1, "ns", "svc", "app", "key")
        val value = PropertyValue(1, "value".toByteArray(), 100L)

        storage.setWithRevision(key, value, 1)
        assertTrue(pathHolder.getKeys().contains("key"))

        storage.remove(key)
        assertFalse(pathHolder.getKeys().contains("key"))
    }

    @Test
    fun `should filter by namespace regex`() {
        storage.setWithRevision(
            PropertyKey(1, "prod", "svc", "app", "key1"),
            PropertyValue(1, "v1".toByteArray(), 100L),
            1
        )
        storage.setWithRevision(
            PropertyKey(1, "dev", "svc", "app", "key2"),
            PropertyValue(1, "v2".toByteArray(), 100L),
            2
        )
        storage.setWithRevision(
            PropertyKey(1, "test", "svc", "app", "key3"),
            PropertyValue(1, "v3".toByteArray(), 100L),
            3
        )

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
        storage.setWithRevision(
            PropertyKey(1, "ns", "auth-service", "app", "key1"),
            PropertyValue(1, "v1".toByteArray(), 100L),
            1
        )
        storage.setWithRevision(
            PropertyKey(1, "ns", "user-service", "app", "key2"),
            PropertyValue(1, "v2".toByteArray(), 100L),
            2
        )
        storage.setWithRevision(
            PropertyKey(1, "ns", "payment-service", "app", "key3"),
            PropertyValue(1, "v3".toByteArray(), 100L),
            3
        )

        val filter = PropertyQueryFilter(
            serviceRegex = ".*-service",
            limit = 10
        )

        val results = storage.getByFilter(filter).toList()

        assertEquals(3, results.size)
    }

    @Test
    fun `should filter by appId regex`() {
        storage.setWithRevision(
            PropertyKey(1, "ns", "svc", "app-1", "key1"),
            PropertyValue(1, "v1".toByteArray(), 100L),
            1
        )
        storage.setWithRevision(
            PropertyKey(1, "ns", "svc", "app-2", "key2"),
            PropertyValue(1, "v2".toByteArray(), 100L),
            2
        )
        storage.setWithRevision(
            PropertyKey(1, "ns", "svc", "other", "key3"),
            PropertyValue(1, "v3".toByteArray(), 100L),
            3
        )

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
        storage.setWithRevision(
            PropertyKey(1, "ns", "svc", "app", "config.db.host"),
            PropertyValue(1, "v1".toByteArray(), 100L),
            1
        )
        storage.setWithRevision(
            PropertyKey(1, "ns", "svc", "app", "config.db.port"),
            PropertyValue(1, "v2".toByteArray(), 100L),
            2
        )
        storage.setWithRevision(
            PropertyKey(1, "ns", "svc", "app", "feature.flag"),
            PropertyValue(1, "v3".toByteArray(), 100L),
            3
        )

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
        storage.setWithRevision(
            PropertyKey(1, "prod", "auth-service", "app1", "key1"),
            PropertyValue(1, "v1".toByteArray(), 100L),
            1
        )
        storage.setWithRevision(
            PropertyKey(1, "prod", "user-service", "app1", "key2"),
            PropertyValue(1, "v2".toByteArray(), 100L),
            2
        )
        storage.setWithRevision(
            PropertyKey(1, "dev", "auth-service", "app1", "key3"),
            PropertyValue(1, "v3".toByteArray(), 100L),
            3
        )
        storage.setWithRevision(
            PropertyKey(1, "prod", "auth-service", "app2", "key4"),
            PropertyValue(1, "v4".toByteArray(), 100L),
            4
        )

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
            storage.setWithRevision(
                PropertyKey(1, "ns", "svc", "app", "key$i"),
                PropertyValue(1, "v$i".toByteArray(), 100L),
                i.toLong()
            )
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
        storage.setWithRevision(
            PropertyKey(1, "ns1", "svc1", "app1", "key1"),
            PropertyValue(1, "v1".toByteArray(), 100L),
            1
        )

        val filter = PropertyQueryFilter(
            namespaceRegex = "non-existent",
            limit = 10
        )

        val results = storage.getByFilter(filter).toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `should return all properties when filter has no regex patterns`() {
        storage.setWithRevision(
            PropertyKey(1, "ns1", "svc1", "app1", "key1"),
            PropertyValue(1, "v1".toByteArray(), 100L),
            1
        )
        storage.setWithRevision(
            PropertyKey(1, "ns2", "svc2", "app2", "key2"),
            PropertyValue(1, "v2".toByteArray(), 100L),
            2
        )
        storage.setWithRevision(
            PropertyKey(1, "ns3", "svc3", "app3", "key3"),
            PropertyValue(1, "v3".toByteArray(), 100L),
            3
        )

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
        storage.setWithRevision(
            key,
            PropertyValue(1, EMPTY_BYTES, 100L),
            1
        )

        val retrieved = storage[key]

        assertNotNull(retrieved)
        assertContentEquals(EMPTY_BYTES, retrieved.value.toByteArray())
    }

    @Test
    fun `should handle large byte array values`() {
        val key = PropertyKey(1, "ns", "svc", "app", "key")
        val largeArray = ByteArray(100000) { it.toByte() }
        val value = PropertyValue(1, largeArray, 100L)
        storage.setWithRevision(
            key,
            value,
            1
        )

        val retrieved = storage[key]

        assertNotNull(retrieved)
        assertContentEquals(largeArray, retrieved.value.toByteArray())
    }

    @Test
    fun `should handle UTF-8 values in keys`() {
        val key = PropertyKey(1, "命名空间", "сервис", "アプリ", "مفتاح")
        val value = PropertyValue(1, "значение 🎉".toByteArray(Charsets.UTF_8), 100L)

        storage.setWithRevision(
            key,
            value,
            1
        )

        val retrieved = storage[key]

        assertNotNull(retrieved)
        assertEquals("значение 🎉", String(retrieved.value.toByteArray(), Charsets.UTF_8))
    }

    @Test
    fun `should handle special characters in keys`() {
        val key = PropertyKey(1, "ns/with/slashes", "svc\\with\\backslashes", "app:with:colons", "key.with.dots")
        val value = PropertyValue(1, "value".toByteArray(), 100L)

        storage.setWithRevision(
            key,
            value,
            1
        )

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
            storage.setWithRevision(
                key,
                value,
                1
            )
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

        storage.setWithRevision(
            key1,
            value1,
            1
        )
        storage.setWithRevision(
            key2,
            value2,
            2
        )
        storage.setWithRevision(
            key3,
            value3,
            3
        )

        assertEquals(Long.MIN_VALUE, storage[key1]?.lastModifiedMs)
        assertEquals(0L, storage[key2]?.lastModifiedMs)
        assertEquals(Long.MAX_VALUE, storage[key3]?.lastModifiedMs)
    }

    @Test
    fun `should handle complex regex patterns in filter`() {
        storage.setWithRevision(
            PropertyKey(1, "prod-eu-west-1", "svc", "app", "key1"),
            PropertyValue(1, "v1".toByteArray(), 100L),
            1
        )
        storage.setWithRevision(
            PropertyKey(1, "prod-us-east-1", "svc", "app", "key2"),
            PropertyValue(1, "v2".toByteArray(), 100L),
            2
        )
        storage.setWithRevision(
            PropertyKey(1, "dev-eu-west-1", "svc", "app", "key3"),
            PropertyValue(1, "v3".toByteArray(), 100L),
            3
        )

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
            storage.setWithRevision(
                PropertyKey(1, "ns", "svc", "app", "key$i"),
                PropertyValue(1, "v$i".toByteArray(), 100L),
                3
            )
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

        storage.setWithRevision(
            key,
            value1,
            1
        )
        assertEquals(value1, storage[key])

        storage.remove(key)
        assertNull(storage[key])

        storage.setWithRevision(
            key,
            value2,
            2
        )
        assertEquals(value2, storage[key])
    }

    @Test
    fun `should handle zero limit in filter`() {
        storage.setWithRevision(
            PropertyKey(1, "ns", "svc", "app", "key1"),
            PropertyValue(1, "v1".toByteArray(), 100L),
            1
        )
        storage.setWithRevision(
            PropertyKey(1, "ns", "svc", "app", "key2"),
            PropertyValue(1, "v2".toByteArray(), 100L),
            2
        )

        val filter = PropertyQueryFilter(
            namespaceRegex = "ns",
            limit = 0
        )

        val results = storage.getByFilter(filter).toList()

        assertEquals(0, results.size)
    }

    // ── setWithRevision ───────────────────────────────────────────────────────

    @Nested
    inner class SetWithRevision {

        @Test
        fun `currentRevision starts at zero`() {
            assertEquals(0L, storage.currentRevision.get())
        }

        @Test
        fun `should store value and update revision atomically`() {
            val key = PropertyKey(1, "ns", "svc", "app", "key")
            val value = PropertyValue(1, "val".toByteArray(), 100L)

            storage.setWithRevision(key, value, revision = 42L)

            assertEquals(value, storage[key])
            assertEquals(42L, storage.currentRevision.get())
        }

        @Test
        fun `should update revision to the latest call`() {
            val key1 = PropertyKey(1, "ns", "svc", "app", "key1")
            val key2 = PropertyKey(1, "ns", "svc", "app", "key2")

            storage.setWithRevision(key1, PropertyValue(1, "v1".toByteArray(), 100L), revision = 10L)
            storage.setWithRevision(key2, PropertyValue(1, "v2".toByteArray(), 200L), revision = 11L)

            assertEquals(11L, storage.currentRevision.get())
        }

        @Test
        fun `should populate partsHolder indices`() {
            val key = PropertyKey(1, "ns", "svc", "app", "key")
            storage.setWithRevision(key, PropertyValue(1, "v".toByteArray(), 1L), revision = 1L)

            assertTrue(pathHolder.getNamespaces().contains("ns"))
            assertTrue(pathHolder.getServices().contains("svc"))
            assertTrue(pathHolder.getAppIds().contains("app"))
            assertTrue(pathHolder.getKeys().contains("key"))
        }
    }

    // ── removeWithRevision ────────────────────────────────────────────────────

    @Nested
    inner class RemoveWithRevision {

        @Test
        fun `should remove value and update revision`() {
            val key = PropertyKey(1, "ns", "svc", "app", "key")
            val value = PropertyValue(1, "val".toByteArray(), 100L)
            storage.setWithRevision(key, value, revision = 1L)

            val removed = storage.removeWithRevision(key, revision = 2L)

            assertEquals(value, removed)
            assertNull(storage[key])
            assertEquals(2L, storage.currentRevision.get())
        }

        @Test
        fun `should return null and NOT update revision when key does not exist`() {
            val key = PropertyKey(1, "ns", "svc", "app", "missing")
            storage.setWithRevision(
                PropertyKey(1, "ns", "svc", "app", "other"),
                PropertyValue(1, "v".toByteArray(), 1L),
                revision = 5L
            )

            val removed = storage.removeWithRevision(key, revision = 99L)

            assertNull(removed)
            assertEquals(5L, storage.currentRevision.get())
        }

        @Test
        fun `should clean up partsHolder when last key for namespace is removed`() {
            val key = PropertyKey(1, "ns", "svc", "app", "key")
            storage.setWithRevision(key, PropertyValue(1, "v".toByteArray(), 1L), revision = 1L)

            storage.removeWithRevision(key, revision = 2L)

            assertFalse(pathHolder.getNamespaces().contains("ns"))
            assertFalse(pathHolder.getServices().contains("svc"))
            assertFalse(pathHolder.getAppIds().contains("app"))
            assertFalse(pathHolder.getKeys().contains("key"))
        }

        @Test
        fun `should keep partsHolder entries when other keys share metadata`() {
            val key1 = PropertyKey(1, "ns", "svc", "app", "key1")
            val key2 = PropertyKey(1, "ns", "svc", "app", "key2")
            storage.setWithRevision(key1, PropertyValue(1, "v1".toByteArray(), 1L), revision = 1L)
            storage.setWithRevision(key2, PropertyValue(1, "v2".toByteArray(), 2L), revision = 2L)

            storage.removeWithRevision(key1, revision = 3L)

            assertTrue(pathHolder.getNamespaces().contains("ns"))
            assertTrue(pathHolder.getKeys().contains("key2"))
            assertFalse(pathHolder.getKeys().contains("key1"))
        }
    }

    // ── getSnapshotData ───────────────────────────────────────────────────────

    @Nested
    inner class GetSnapshotData {

        @Test
        fun `should return zero revision and empty list for empty storage`() {
            val (revision, entries) = storage.getSnapshotData()

            assertEquals(0L, revision)
            assertTrue(entries.isEmpty())
        }

        @Test
        fun `should return all entries with current revision`() {
            val key1 = PropertyKey(1, "ns", "svc", "app", "key1")
            val key2 = PropertyKey(1, "ns", "svc", "app", "key2")
            val value1 = PropertyValue(1, "v1".toByteArray(), 100L)
            val value2 = PropertyValue(1, "v2".toByteArray(), 200L)

            storage.setWithRevision(key1, value1, revision = 10L)
            storage.setWithRevision(key2, value2, revision = 11L)

            val (revision, entries) = storage.getSnapshotData()

            assertEquals(11L, revision)
            assertEquals(2, entries.size)
            assertTrue(entries.any { it.key == key1 && it.value == value1 })
            assertTrue(entries.any { it.key == key2 && it.value == value2 })
        }

        @Test
        fun `snapshot revision should match last setWithRevision call`() {
            storage.setWithRevision(PropertyKey(1, "ns", "svc", "app", "k1"), PropertyValue(1, "v".toByteArray(), 1L), revision = 7L)
            storage.setWithRevision(PropertyKey(1, "ns", "svc", "app", "k2"), PropertyValue(1, "v".toByteArray(), 2L), revision = 8L)
            storage.removeWithRevision(PropertyKey(1, "ns", "svc", "app", "k1"), revision = 9L)

            val (revision, entries) = storage.getSnapshotData()

            assertEquals(9L, revision)
            assertEquals(1, entries.size)
        }
    }

    // ── restoreFromSnapshot ───────────────────────────────────────────────────

    @Nested
    inner class RestoreFromSnapshot {

        @Test
        fun `should restore entries and revision from snapshot`() {
            val entries = listOf(
                PropertyInternalDto(PropertyKey(1, "ns", "svc", "app", "key1"), PropertyValue(1, "v1".toByteArray(), 100L)),
                PropertyInternalDto(PropertyKey(1, "ns", "svc", "app", "key2"), PropertyValue(1, "v2".toByteArray(), 200L))
            )

            storage.restoreFromSnapshot(entries, revision = 42L)

            assertEquals(42L, storage.currentRevision.get())
            assertEquals(entries[0].value, storage[entries[0].key])
            assertEquals(entries[1].value, storage[entries[1].key])
        }

        @Test
        fun `should clear existing state before restoring`() {
            storage.setWithRevision(PropertyKey(1, "old-ns", "old-svc", "old-app", "old-key"), PropertyValue(1, "old".toByteArray(), 1L), revision = 1L)

            val newEntry = PropertyInternalDto(
                PropertyKey(1, "new-ns", "new-svc", "new-app", "new-key"),
                PropertyValue(1, "new".toByteArray(), 2L)
            )
            storage.restoreFromSnapshot(listOf(newEntry), revision = 10L)

            assertNull(storage[PropertyKey(1, "old-ns", "old-svc", "old-app", "old-key")])
            assertEquals(newEntry.value, storage[newEntry.key])
            assertEquals(10L, storage.currentRevision.get())
        }

        @Test
        fun `should restore partsHolder indices from snapshot`() {
            val entries = listOf(
                PropertyInternalDto(PropertyKey(1, "ns1", "svc1", "app1", "key1"), PropertyValue(1, "v1".toByteArray(), 100L)),
                PropertyInternalDto(PropertyKey(1, "ns2", "svc2", "app2", "key2"), PropertyValue(1, "v2".toByteArray(), 200L))
            )

            storage.restoreFromSnapshot(entries, revision = 5L)

            assertTrue(pathHolder.getNamespaces().containsAll(setOf("ns1", "ns2")))
            assertTrue(pathHolder.getServices().containsAll(setOf("svc1", "svc2")))
            assertTrue(pathHolder.getAppIds().containsAll(setOf("app1", "app2")))
            assertTrue(pathHolder.getKeys().containsAll(setOf("key1", "key2")))
        }

        @Test
        fun `should clear partsHolder indices of old entries after restore`() {
            storage.setWithRevision(PropertyKey(1, "old-ns", "old-svc", "old-app", "old-key"), PropertyValue(1, "v".toByteArray(), 1L), revision = 1L)

            storage.restoreFromSnapshot(emptyList(), revision = 2L)

            assertTrue(pathHolder.getNamespaces().isEmpty())
            assertTrue(pathHolder.getServices().isEmpty())
            assertTrue(pathHolder.getAppIds().isEmpty())
            assertTrue(pathHolder.getKeys().isEmpty())
        }

        @Test
        fun `restoring empty snapshot should reset revision to given value`() {
            storage.setWithRevision(PropertyKey(1, "ns", "svc", "app", "key"), PropertyValue(1, "v".toByteArray(), 1L), revision = 99L)

            storage.restoreFromSnapshot(emptyList(), revision = 0L)

            assertEquals(0L, storage.currentRevision.get())
            assertNull(storage[PropertyKey(1, "ns", "svc", "app", "key")])
        }

        @Test
        fun `roundtrip getSnapshotData then restoreFromSnapshot should produce identical state`() {
            val key1 = PropertyKey(1, "ns1", "svc1", "app1", "key1")
            val key2 = PropertyKey(1, "ns2", "svc2", "app2", "key2")
            val value1 = PropertyValue(1, "v1".toByteArray(), 100L)
            val value2 = PropertyValue(1, "v2".toByteArray(), 200L)
            storage.setWithRevision(key1, value1, revision = 10L)
            storage.setWithRevision(key2, value2, revision = 11L)

            val (snapshotRevision, snapshotEntries) = storage.getSnapshotData()

            val freshStorage = PropertyInMemoryStorage(PropertyPartsHolder())
            freshStorage.restoreFromSnapshot(snapshotEntries, snapshotRevision)

            val (restoredRevision, restoredEntries) = freshStorage.getSnapshotData()
            assertEquals(snapshotRevision, restoredRevision)
            assertEquals(snapshotEntries.size, restoredEntries.size)
            snapshotEntries.forEach { original ->
                val restored = restoredEntries.find { it.key == original.key }
                assertNotNull(restored)
                assertContentEquals(original.value.value.toByteArray(), restored.value.value.toByteArray())
                assertEquals(original.value.lastModifiedMs, restored.value.lastModifiedMs)
            }
        }
    }
}
