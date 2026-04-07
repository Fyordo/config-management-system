package com.fyordo.cms.sdk.javasdk.property.repo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PropertyRepository {
    @Nullable
    String getByKey(@NotNull String key);

    @Nullable
    String store(@NotNull String key,
                 @Nullable String newValue);
}
