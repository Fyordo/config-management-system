package com.fyordo.cms.sdk.javasdk.sock;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public final class PropertyUpdateMessage {

    private final String key;
    private final byte[] value;

    public PropertyUpdateMessage(@NotNull String key, byte[] value) {
        this.key = Objects.requireNonNull(key);
        this.value = value != null ? value.clone() : new byte[0];
    }

    @NotNull
    public String getKey() {
        return key;
    }

    @NotNull
    public byte[] getValue() {
        return value.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyUpdateMessage that = (PropertyUpdateMessage) o;
        return key.equals(that.key) && Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        int result = key.hashCode();
        result = 31 * result + Arrays.hashCode(value);
        return result;
    }
}
