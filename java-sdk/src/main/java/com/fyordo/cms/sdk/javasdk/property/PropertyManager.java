package com.fyordo.cms.sdk.javasdk.property;

import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PropertyManager {
    private final Map<String, PropertyUpdateCallback> callbacks;
    private final PropertyRepository repository;
    private final PropertyUpdateCallback defaultCallback;

    public PropertyManager(@NotNull PropertyRepository repository) {
        this.repository = Objects.requireNonNull(repository);
        this.callbacks = new HashMap<>();
        this.defaultCallback = (_, _, _) -> {
        };
    }

    public PropertyManager(@NotNull PropertyRepository repository,
                           @NotNull PropertyUpdateCallback defaultCallback) {
        this.repository = Objects.requireNonNull(repository);
        this.callbacks = new HashMap<>();
        this.defaultCallback = Objects.requireNonNull(defaultCallback);
    }

    public void readFromFile() {

    }

    public void addUpdateCallback(@NotNull String key,
                                  @Nullable PropertyUpdateCallback callback) {
        callbacks.put(key, callback);
    }

    @Nullable
    public <T> T get(@NotNull String key) {
        return (T) repository.getByKey(key);
    }

    private void store(@NotNull String key,
                       @Nullable Object newValue) {
        Object oldValue = repository.store(key, newValue);
        callbacks.getOrDefault(key, defaultCallback)
                .apply(key, oldValue, newValue);
    }

    private boolean tryingToInsertNullPrimitive(@Nullable Object newValue, @Nullable Object oldValue) {
        return oldValue != null && oldValue.getClass().isPrimitive() && newValue == null;
    }
}
