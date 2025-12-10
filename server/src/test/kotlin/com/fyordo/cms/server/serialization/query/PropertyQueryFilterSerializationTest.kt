package com.fyordo.cms.server.serialization.query

import com.fyordo.cms.server.dto.query.PropertyQueryFilter
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PropertyQueryFilterSerializationTest {

    @Test
    fun `should serialize and deserialize PropertyQueryFilter with all fields`() {
        val filter = PropertyQueryFilter(
            namespaceRegex = "test-namespace.*",
            serviceRegex = "test-service.*",
            appIdRegex = "test-app.*",
            keyRegex = "test-key.*",
            valueRegex = "test-value.*",
            limit = 100
        )

        val serialized = serializePropertyQueryFilter(filter)
        val deserialized = deserializePropertyQueryFilter(serialized)

        assertEquals(filter, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyQueryFilter with all nulls`() {
        val filter = PropertyQueryFilter(
            namespaceRegex = null,
            serviceRegex = null,
            appIdRegex = null,
            keyRegex = null,
            valueRegex = null,
            limit = 10
        )

        val serialized = serializePropertyQueryFilter(filter)
        val deserialized = deserializePropertyQueryFilter(serialized)

        assertEquals(filter, deserialized)
        assertNull(deserialized.namespaceRegex)
        assertNull(deserialized.serviceRegex)
        assertNull(deserialized.appIdRegex)
        assertNull(deserialized.keyRegex)
        assertNull(deserialized.valueRegex)
    }

    @Test
    fun `should serialize and deserialize PropertyQueryFilter with mixed null and non-null values`() {
        val filter = PropertyQueryFilter(
            namespaceRegex = "ns.*",
            serviceRegex = null,
            appIdRegex = "app.*",
            keyRegex = null,
            valueRegex = "val.*",
            limit = 50
        )

        val serialized = serializePropertyQueryFilter(filter)
        val deserialized = deserializePropertyQueryFilter(serialized)

        assertEquals(filter, deserialized)
        assertEquals("ns.*", deserialized.namespaceRegex)
        assertNull(deserialized.serviceRegex)
        assertEquals("app.*", deserialized.appIdRegex)
        assertNull(deserialized.keyRegex)
        assertEquals("val.*", deserialized.valueRegex)
    }

    @Test
    fun `should serialize and deserialize PropertyQueryFilter with empty strings`() {
        val filter = PropertyQueryFilter(
            namespaceRegex = "",
            serviceRegex = "",
            appIdRegex = "",
            keyRegex = "",
            valueRegex = "",
            limit = 1
        )

        val serialized = serializePropertyQueryFilter(filter)
        val deserialized = deserializePropertyQueryFilter(serialized)

        // Note: The serialization treats empty strings as null (size 0 = null)
        // This is by design in the serialization format
        assertEquals(1, deserialized.limit)
        assertNull(deserialized.namespaceRegex)
        assertNull(deserialized.serviceRegex)
        assertNull(deserialized.appIdRegex)
        assertNull(deserialized.keyRegex)
        assertNull(deserialized.valueRegex)
    }

    @Test
    fun `should serialize and deserialize PropertyQueryFilter with UTF-8 regex patterns`() {
        val filter = PropertyQueryFilter(
            namespaceRegex = "命名空间.*",
            serviceRegex = "сервис.*",
            appIdRegex = "アプリ.*",
            keyRegex = "مفتاح.*",
            valueRegex = "значение.*🎉",
            limit = 25
        )

        val serialized = serializePropertyQueryFilter(filter)
        val deserialized = deserializePropertyQueryFilter(serialized)

        assertEquals(filter, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyQueryFilter with complex regex patterns`() {
        val filter = PropertyQueryFilter(
            namespaceRegex = "^[a-z]+-namespace$",
            serviceRegex = "(service1|service2|service3)",
            appIdRegex = "app-[0-9]{3}",
            keyRegex = "key\\.[a-zA-Z]+\\.[0-9]+",
            valueRegex = ".*\\d{4}-\\d{2}-\\d{2}.*",
            limit = 200
        )

        val serialized = serializePropertyQueryFilter(filter)
        val deserialized = deserializePropertyQueryFilter(serialized)

        assertEquals(filter, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyQueryFilter with very long regex patterns`() {
        val longRegex = "pattern".repeat(1000)
        val filter = PropertyQueryFilter(
            namespaceRegex = longRegex,
            serviceRegex = longRegex,
            appIdRegex = longRegex,
            keyRegex = longRegex,
            valueRegex = longRegex,
            limit = 1000
        )

        val serialized = serializePropertyQueryFilter(filter)
        val deserialized = deserializePropertyQueryFilter(serialized)

        assertEquals(filter, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyQueryFilter with zero limit`() {
        val filter = PropertyQueryFilter(
            namespaceRegex = "test",
            serviceRegex = "test",
            appIdRegex = "test",
            keyRegex = "test",
            valueRegex = "test",
            limit = 0
        )

        val serialized = serializePropertyQueryFilter(filter)
        val deserialized = deserializePropertyQueryFilter(serialized)

        assertEquals(filter, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyQueryFilter with maximum integer limit`() {
        val filter = PropertyQueryFilter(
            namespaceRegex = "test",
            serviceRegex = "test",
            appIdRegex = "test",
            keyRegex = "test",
            valueRegex = "test",
            limit = Int.MAX_VALUE
        )

        val serialized = serializePropertyQueryFilter(filter)
        val deserialized = deserializePropertyQueryFilter(serialized)

        assertEquals(filter, deserialized)
    }

    @Test
    fun `should serialize and deserialize PropertyQueryFilter with default limit`() {
        val filter = PropertyQueryFilter(
            namespaceRegex = "test",
            serviceRegex = null,
            appIdRegex = null,
            keyRegex = null,
            valueRegex = null
        )

        val serialized = serializePropertyQueryFilter(filter)
        val deserialized = deserializePropertyQueryFilter(serialized)

        assertEquals(10, deserialized.limit)
    }

    @Test
    fun `should serialize and deserialize PropertyQueryFilter with special regex characters`() {
        val filter = PropertyQueryFilter(
            namespaceRegex = ".*?+[]{}()|^$\\",
            serviceRegex = "test\\.service\\[1\\]",
            appIdRegex = "app\\|id",
            keyRegex = "key\\(test\\)",
            valueRegex = "value\\{1,5\\}",
            limit = 15
        )

        val serialized = serializePropertyQueryFilter(filter)
        val deserialized = deserializePropertyQueryFilter(serialized)

        assertEquals(filter, deserialized)
    }

    @Test
    fun `should handle multiple round trips without data corruption`() {
        val filter = PropertyQueryFilter(
            namespaceRegex = "ns.*",
            serviceRegex = null,
            appIdRegex = "app[0-9]+",
            keyRegex = null,
            valueRegex = "val.*",
            limit = 42
        )

        var serialized = serializePropertyQueryFilter(filter)
        repeat(5) {
            val deserialized = deserializePropertyQueryFilter(serialized)
            serialized = serializePropertyQueryFilter(deserialized)
        }
        val final = deserializePropertyQueryFilter(serialized)

        assertEquals(filter, final)
    }

    @Test
    fun `should treat empty string as null in serialization`() {
        val filterWithNull = PropertyQueryFilter(
            namespaceRegex = null,
            serviceRegex = null,
            appIdRegex = null,
            keyRegex = null,
            valueRegex = null,
            limit = 10
        )

        val filterWithEmpty = PropertyQueryFilter(
            namespaceRegex = "",
            serviceRegex = "",
            appIdRegex = "",
            keyRegex = "",
            valueRegex = "",
            limit = 10
        )

        val serializedNull = serializePropertyQueryFilter(filterWithNull)
        val deserializedNull = deserializePropertyQueryFilter(serializedNull)

        val serializedEmpty = serializePropertyQueryFilter(filterWithEmpty)
        val deserializedEmpty = deserializePropertyQueryFilter(serializedEmpty)

        // Both empty strings and nulls are deserialized as null
        // This is by design - the serialization format uses size 0 to represent null
        assertNull(deserializedNull.namespaceRegex)
        assertNull(deserializedEmpty.namespaceRegex)

        // The serialized forms should be identical
        assertContentEquals(serializedNull, serializedEmpty)
    }
}
