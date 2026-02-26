package com.fyordo.cms.sdk.javasdk.sock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyUpdateMessageTest {

    @Test
    void storesKeyAndCloneOfValue() {
        byte[] original = new byte[]{1, 2, 3};
        PropertyUpdateMessage msg = new PropertyUpdateMessage("key", original);

        assertEquals("key", msg.getKey());

        byte[] value1 = msg.getValue();
        assertArrayEquals(new byte[]{1, 2, 3}, value1);

        // Mutate original and ensure internal value is not affected
        original[0] = 9;
        byte[] value2 = msg.getValue();
        assertArrayEquals(new byte[]{1, 2, 3}, value2);

        // Mutate returned array and ensure subsequent calls still return original data
        value2[1] = 8;
        byte[] value3 = msg.getValue();
        assertArrayEquals(new byte[]{1, 2, 3}, value3);
    }

    @Test
    void equalsAndHashCodeDependOnKeyAndValue() {
        PropertyUpdateMessage a = new PropertyUpdateMessage("k", new byte[]{1, 2});
        PropertyUpdateMessage b = new PropertyUpdateMessage("k", new byte[]{1, 2});
        PropertyUpdateMessage c = new PropertyUpdateMessage("k2", new byte[]{1, 2});
        PropertyUpdateMessage d = new PropertyUpdateMessage("k", new byte[]{1, 3});

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        assertNotEquals(a, c);
        assertNotEquals(a, d);
    }
}

