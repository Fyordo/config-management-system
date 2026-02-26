package com.fyordo.cms.sdk.javasdk.property;

import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepository;
import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepositoryImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PropertyManagerReadTest {
    @Test
    public void testEmpty() {
        PropertyRepository repository = new PropertyRepositoryImpl();
        PropertyManager manager = new PropertyManager(repository);

        assertNull(manager.get("app.nonExistingProperty"));
    }

    @Test
    public void testSimple() {
        PropertyRepository repository = new PropertyRepositoryImpl();
        PropertyManager manager = new PropertyManager(repository);

        repository.store("app.byteProperty", (byte) 123);
        repository.store("app.intProperty", 123);
        repository.store("app.stringProperty", "SomeRandomString");
        repository.store("app.longProperty", 123123123123123L);

        assertEquals((byte) 123, (Byte) manager.get("app.byteProperty"));
        assertEquals(123, (Integer) manager.get("app.intProperty"));
        assertEquals("SomeRandomString", manager.get("app.stringProperty"));
        assertEquals(123123123123123L, (Long) manager.get("app.longProperty"));
    }

    @Test
    public void testCollections() {
        PropertyRepository repository = new PropertyRepositoryImpl();
        PropertyManager manager = new PropertyManager(repository);

        repository.store("app.list", List.of(1, 2, 3, 4));
        repository.store("app.set", Set.of(1, 2, 3, 4));
        repository.store("app.map", Map.of(1, 2, 3, 4));

        List<Integer> list = manager.get("app.list");
        assertNotNull(list);
        assertEquals(4, list.size());

        Set<Integer> set = manager.get("app.set");
        assertNotNull(set);
        assertEquals(4, set.size());

        Map<Integer, Integer> map = manager.get("app.map");
        assertNotNull(map);
        assertEquals(2, map.size());
    }

    @Test
    public void testChangeToNull() {
        PropertyRepository repository = new PropertyRepositoryImpl();
        PropertyManager manager = new PropertyManager(repository);

        repository.store("app.intProperty", 123);

        assertEquals(123, (Integer) manager.get("app.intProperty"));

        repository.store("app.intProperty", null);

        assertNull(manager.get("app.intProperty"));
    }
}
