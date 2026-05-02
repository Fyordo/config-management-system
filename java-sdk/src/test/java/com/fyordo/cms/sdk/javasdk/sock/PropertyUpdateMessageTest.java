package com.fyordo.cms.sdk.javasdk.sock;

import com.fyordo.cms.CmsProto;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyUpdateMessageTest {

    @NotNull
    private CmsProto.Property property(@NotNull String key, @NotNull byte[] value) {
        return CmsProto.Property.newBuilder()
                .setKey(key)
                .setValue(ByteString.copyFrom(value))
                .setModifiedMs(System.currentTimeMillis())
                .build();
    }

    @Test
    void storesKeyAndCloneOfValue() {
        byte[] original = new byte[]{1, 2, 3};
        CmsProto.Property msg = property("key", original);

        assertEquals("key", msg.getKey());

        byte[] value1 = msg.getValue().toByteArray();
        assertArrayEquals(new byte[]{1, 2, 3}, value1);

        // Mutate original and ensure internal value is not affected
        original[0] = 9;
        byte[] value2 = msg.getValue().toByteArray();
        assertArrayEquals(new byte[]{1, 2, 3}, value2);

        // Mutate returned array and ensure subsequent calls still return original data
        value2[1] = 8;
        byte[] value3 = msg.getValue().toByteArray();
        assertArrayEquals(new byte[]{1, 2, 3}, value3);
    }

    @Test
    void equalsAndHashCodeDependOnKeyAndValue() {
        CmsProto.Property a = property("k", new byte[]{1, 2});
        CmsProto.Property b = property("k", new byte[]{1, 2});
        CmsProto.Property c = property("k2", new byte[]{1, 2});
        CmsProto.Property d = property("k", new byte[]{1, 3});

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        assertNotEquals(a, c);
        assertNotEquals(a, d);
    }
}

