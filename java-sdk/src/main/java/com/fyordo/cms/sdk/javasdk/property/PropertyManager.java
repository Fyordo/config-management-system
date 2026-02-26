package com.fyordo.cms.sdk.javasdk.property;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PropertyManager {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, PropertyUpdateCallback> callbacks;
    private final PropertyRepository repository;
    private final PropertyUpdateCallback defaultCallback;
    private final Path configFilePath;

    public PropertyManager(@NotNull PropertyRepository repository,
                           @NotNull String configFilePath) {
        this(repository, configFilePath, (_, _, _) -> {
        });
    }

    public PropertyManager(@NotNull PropertyRepository repository,
                           @NotNull String configFilePath,
                           @NotNull PropertyUpdateCallback defaultCallback) {
        this.repository = Objects.requireNonNull(repository);
        this.callbacks = new HashMap<>();
        this.defaultCallback = Objects.requireNonNull(defaultCallback);
        this.configFilePath = Path.of(Objects.requireNonNull(configFilePath));
    }

    public void readFromFile() {
        try {
            if (!Files.exists(configFilePath)) {
                throw new IllegalArgumentException("File doesn't exists");
            }

            String json = Files.readString(configFilePath, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                throw new IllegalArgumentException("File is blank");
            }

            Map<String, Object> values = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });

            for (Map.Entry<String, Object> entry : values.entrySet()) {
                store(entry.getKey(), entry.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read properties from file " + configFilePath, e);
        }
    }

    public void addUpdateCallback(@NotNull String key,
                                  @Nullable PropertyUpdateCallback callback) {
        callbacks.put(key, callback);
    }

    @Nullable
    public <T> T get(@NotNull String key) {
        return (T) repository.getByKey(key);
    }

    public void store(@NotNull String key,
                       @Nullable Object newValue) {
        Object oldValue = repository.store(key, newValue);
        callbacks.getOrDefault(key, defaultCallback)
                .apply(key, oldValue, newValue);
    }
}
