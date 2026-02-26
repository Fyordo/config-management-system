package com.fyordo.cms.sdk.javasdk.property.repo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PropertyRepository {
    @Nullable
    Object getByKey(@NotNull String key);

    @Nullable
    Object store(@NotNull String key,
                 @Nullable Object newValue);
}
