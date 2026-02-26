package com.fyordo.cms.sdk.javasdk.property;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface PropertyUpdateCallback {
    void apply(@NotNull String key, @Nullable Object oldValue, @Nullable Object newValue);
}
