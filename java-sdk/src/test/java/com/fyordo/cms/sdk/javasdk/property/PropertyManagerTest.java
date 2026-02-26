package com.fyordo.cms.sdk.javasdk.property;

import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepository;
import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepositoryImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PropertyManagerTest {
    @Test
    public void readFromBlankFile() throws URISyntaxException {
        PropertyRepository repository = new PropertyRepositoryImpl();
        PropertyManager propertyManager = new PropertyManager(
                repository,
                getFilePath("empty.json"),
                getSocketPath("pm-test.sock")
        );
        assertThrows(IllegalArgumentException.class, propertyManager::readFromFile);
    }

    @Test
    public void readFromFile() throws URISyntaxException {
        PropertyRepository repository = new PropertyRepositoryImpl();
        PropertyManager propertyManager = new PropertyManager(
                repository,
                getFilePath("application.json"),
                getSocketPath("pm-test.sock")
        );
        propertyManager.readFromFile();
        Integer intVal = propertyManager.get("app.int.val");
        Long longVal = propertyManager.get("app.long.val");
        String stringVal = propertyManager.get("app.string.val");
        List<Integer> listVal = propertyManager.get("app.list.val");
        Map<String, Object> objectVal = propertyManager.get("app.object.val");
        TestEnum enumVal = TestEnum.valueOf(propertyManager.get("app.enum.val"));

        assertNotNull(intVal);
        assertEquals(123, intVal);
        assertNotNull(longVal);
        assertEquals(123123123123L, longVal);
        assertNotNull(stringVal);
        assertEquals("SomeRandomString", stringVal);
        assertNotNull(listVal);
        assertEquals(4, listVal.size());
        assertNotNull(objectVal);
        assertEquals(3, objectVal.size());
        assertNotNull(enumVal);
        assertEquals(TestEnum.TYPE_1, enumVal);
    }

    private enum TestEnum {
        TYPE_1, TYPE_2, TYPE_3
    }

    @NotNull
    private String getFilePath(@NotNull String fileName) throws URISyntaxException {
        return Paths.get(
                Objects.requireNonNull(getClass().getClassLoader().getResource(fileName)).toURI()
        ).toString();
    }

    @NotNull
    private String getSocketPath(@NotNull String name) {
        return Paths.get(System.getProperty("java.io.tmpdir"), name).toString();
    }

}
