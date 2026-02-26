package com.fyordo.cms.sdk.javasdk.property.repo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PropertyRepositoryImplSimpleTest {
    @Test
    public void testEmpty() {
        PropertyRepository repository = new PropertyRepositoryImpl();

        assertNull(repository.getByKey("app.nonExistingProperty"));
    }

    @Test
    public void testSimple() {
        PropertyRepository repository = new PropertyRepositoryImpl();

        repository.store("app.byteProperty", (byte) 123);
        repository.store("app.intProperty", 123);
        repository.store("app.stringProperty", "SomeRandomString");
        repository.store("app.longProperty", 123123123123123L);

        assertEquals((byte) 123, (Byte) repository.getByKey("app.byteProperty"));
        assertEquals(123, (Integer) repository.getByKey("app.intProperty"));
        assertEquals("SomeRandomString", repository.getByKey("app.stringProperty"));
        assertEquals(123123123123123L, (Long) repository.getByKey("app.longProperty"));
    }

    @Test
    public void testCollections() {
        PropertyRepository repository = new PropertyRepositoryImpl();

        repository.store("app.list", List.of(1, 2, 3, 4));
        repository.store("app.set", Set.of(1, 2, 3, 4));
        repository.store("app.map", Map.of(1, 2, 3, 4));

        List<Integer> list = (List<Integer>) repository.getByKey("app.list");
        assertNotNull(list);
        assertEquals(4, list.size());

        Set<Integer> set = (Set<Integer>) repository.getByKey("app.set");
        assertNotNull(set);
        assertEquals(4, set.size());

        Map<Integer, Integer> map = (Map<Integer, Integer>) repository.getByKey("app.map");
        assertNotNull(map);
        assertEquals(2, map.size());
    }

    @Test
    public void testRemove() {
        PropertyRepository repository = new PropertyRepositoryImpl();

        repository.store("app.intProperty", 123);

        assertEquals(123, (Integer) repository.getByKey("app.intProperty"));

        repository.store("app.intProperty", null);

        assertNull(repository.getByKey("app.intProperty"));
    }

    @Test
    public void testBigValue() {
        PropertyRepository repository = new PropertyRepositoryImpl();

        repository.store("app.hugeProperty", new byte[1000000]);

        Object array = repository.getByKey("app.hugeProperty");
        assertNotNull(array);
        assertEquals(1000000, ((byte[]) array).length);
    }

    @Test
    public void testCustomClass() {
        PropertyRepository repository = new PropertyRepositoryImpl();

        repository.store("app.someClass", new TestClass(1, 23.45, "String"));

        Object object = repository.getByKey("app.someClass");
        assertNotNull(object);
        TestClass testClass = (TestClass) object;
        assertEquals(1, testClass.field1);
        assertEquals(23.45, testClass.field2);
        assertEquals("String", testClass.field3);
    }

    private static class TestClass {
        private final int field1;
        private final double field2;
        private final String field3;

        public TestClass(int field1, double field2, String field3) {
            this.field1 = field1;
            this.field2 = field2;
            this.field3 = field3;
        }
    }
}
