package com.fyordo.cms.sdk.javasdk.property.repo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PropertyRepositoryImpl implements PropertyRepository {
    private final ConcurrentMap<String, Object> properties;

    public PropertyRepositoryImpl() {
        this.properties = new ConcurrentHashMap<>();
    }

    @Nullable
    public Object getByKey(@NotNull String key) {
        return properties.getOrDefault(key, null);
    }

    @Nullable
    public Object store(@NotNull String key,
                        @Nullable Object newValue) {
        if (newValue == null) {
            return properties.remove(key);
        }
        return properties.put(key, newValue);
    }
}
