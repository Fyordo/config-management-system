package com.fyordo.cms.sdk.javasdk.property;

import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepository;
import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepositoryImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

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
        String intValStr = propertyManager.get("app.int.val");
        assertNotNull(intValStr);
        Integer intVal = Integer.parseInt(intValStr);
        assertEquals(123, intVal);

        String longValStr = propertyManager.get("app.long.val");
        assertNotNull(longValStr);
        Long longVal = Long.parseLong(longValStr);
        assertEquals(123123123123L, longVal);

        String stringVal = propertyManager.get("app.string.val");
        assertEquals("SomeRandomString", stringVal);

        String listValStr = propertyManager.get("app.list.val");
        assertNotNull(listValStr);
        List<Integer> listVal = Stream.of(listValStr.split(",")).map(Integer::parseInt).toList();
        assertEquals(4, listVal.size());

        TestEnum enumVal = TestEnum.valueOf(propertyManager.get("app.enum.val"));
        assertNotNull(enumVal);
        assertEquals(TestEnum.TYPE_1, enumVal);
    }

    @Test
    public void defaultCallbackInvokedOnStore() {
        PropertyRepository repository = new PropertyRepositoryImpl();
        AtomicReference<String> lastKey = new AtomicReference<>();
        AtomicReference<Object> lastOld = new AtomicReference<>();
        AtomicReference<Object> lastNew = new AtomicReference<>();

        PropertyManager propertyManager = new PropertyManager(
                repository,
                getFilePathUnchecked("application.json"),
                getSocketPath("pm-test-callback.sock"),
                (key, oldVal, newVal) -> {
                    lastKey.set(key);
                    lastOld.set(oldVal);
                    lastNew.set(newVal);
                }
        );

        propertyManager.store("k1", "v1");
        assertEquals("k1", lastKey.get());
        assertNull(lastOld.get());
        assertEquals("v1", lastNew.get());

        propertyManager.store("k1", "v2");
        assertEquals("k1", lastKey.get());
        assertEquals("v1", lastOld.get());
        assertEquals("v2", lastNew.get());
    }

    @Test
    public void perKeyCallbackOverridesDefault() {
        PropertyRepository repository = new PropertyRepositoryImpl();
        AtomicInteger defaultCalls = new AtomicInteger();
        AtomicInteger specificCalls = new AtomicInteger();

        PropertyManager propertyManager = new PropertyManager(
                repository,
                getFilePathUnchecked("application.json"),
                getSocketPath("pm-test-callback.sock"),
                (key, oldVal, newVal) -> defaultCalls.incrementAndGet()
        );

        propertyManager.addUpdateCallback("k1", (key, oldVal, newVal) -> specificCalls.incrementAndGet());

        propertyManager.store("k1", "v1");
        propertyManager.store("k2", "v2");

        assertEquals(1, specificCalls.get());
        assertEquals(1, defaultCalls.get());
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
    private String getFilePathUnchecked(@NotNull String fileName) {
        try {
            return getFilePath(fileName);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private String getSocketPath(@NotNull String name) {
        return Paths.get(System.getProperty("java.io.tmpdir"), name).toString();
    }

}
