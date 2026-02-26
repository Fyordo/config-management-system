package com.fyordo.cms.sdk.javasdk.sock;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PropertyUpdateStreamReaderTest {

    @Test
    void readsSingleMessageAndThenEof() throws IOException {
        byte[] stream = buildStream(
                new PropertyUpdateMessage("key", new byte[]{10, 20, 30})
        );

        PropertyUpdateStreamReader reader = new PropertyUpdateStreamReader(new ByteArrayInputStream(stream));

        Optional<PropertyUpdateMessage> first = reader.readMessage();
        assertTrue(first.isPresent());
        assertEquals("key", first.get().getKey());
        assertArrayEquals(new byte[]{10, 20, 30}, first.get().getValue());

        Optional<PropertyUpdateMessage> second = reader.readMessage();
        assertTrue(second.isEmpty());
    }

    @Test
    void readsMultipleMessagesSequentially() throws IOException {
        byte[] stream = buildStream(
                new PropertyUpdateMessage("k1", new byte[]{1}),
                new PropertyUpdateMessage("k2", new byte[]{2, 3})
        );

        PropertyUpdateStreamReader reader = new PropertyUpdateStreamReader(new ByteArrayInputStream(stream));

        Optional<PropertyUpdateMessage> m1 = reader.readMessage();
        Optional<PropertyUpdateMessage> m2 = reader.readMessage();
        Optional<PropertyUpdateMessage> m3 = reader.readMessage();

        assertTrue(m1.isPresent());
        assertEquals("k1", m1.get().getKey());
        assertArrayEquals(new byte[]{1}, m1.get().getValue());

        assertTrue(m2.isPresent());
        assertEquals("k2", m2.get().getKey());
        assertArrayEquals(new byte[]{2, 3}, m2.get().getValue());

        assertTrue(m3.isEmpty());
    }

    @Test
    void eofInMiddleOfKeyBytesThrowsEofException() throws IOException {
        byte[] goodStream = buildStream(
                new PropertyUpdateMessage("abc", new byte[]{1})
        );

        // Truncate so that we cut inside key bytes (after length header)
        int cutPosition = 4 + 2; // 4 bytes length + first 2 bytes of key
        byte[] truncated = new byte[cutPosition];
        System.arraycopy(goodStream, 0, truncated, 0, cutPosition);

        PropertyUpdateStreamReader reader = new PropertyUpdateStreamReader(new ByteArrayInputStream(truncated));

        assertThrows(EOFException.class, reader::readMessage);
    }

    @Test
    void eofBetweenKeyAndValueLengthThrowsIOException() throws IOException {
        byte[] goodStream = buildStream(
                new PropertyUpdateMessage("abc", new byte[]{1, 2})
        );

        // Up to and including key bytes, but without value length bytes
        int keyLen = "abc".getBytes().length;
        int cutPosition = 4 + keyLen; // 4 bytes key length + key bytes, no value length
        byte[] truncated = new byte[cutPosition];
        System.arraycopy(goodStream, 0, truncated, 0, cutPosition);

        PropertyUpdateStreamReader reader = new PropertyUpdateStreamReader(new ByteArrayInputStream(truncated));

        IOException ex = assertThrows(IOException.class, reader::readMessage);
        assertTrue(ex.getMessage().contains("Unexpected EOF"));
    }

    private static byte[] buildStream(PropertyUpdateMessage... messages) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (PropertyUpdateMessage msg : messages) {
            byte[] keyBytes = msg.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] valueBytes = msg.getValue();

            writeInt(out, keyBytes.length);
            out.write(keyBytes);
            writeInt(out, valueBytes.length);
            out.write(valueBytes);
        }
        return out.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream out, int value) throws IOException {
        out.write((value >>> 24) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }
}

