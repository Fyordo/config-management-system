package com.fyordo.cms.sdk.javasdk.sock;

import org.jetbrains.annotations.NotNull;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public final class PropertyUpdateStreamReader {

    private static final int MAX_LENGTH = Integer.MAX_VALUE;

    private final InputStream in;
    private final byte[] lenBuf = new byte[4];

    public PropertyUpdateStreamReader(@NotNull InputStream in) {
        this.in = Objects.requireNonNull(in);
    }

    private int readLength() throws IOException {
        int n = readFully(lenBuf, 4);
        if (n < 0) {
            return -1; // EOF at message boundary
        }
        long u = (lenBuf[0] & 0xffL) << 24 | (lenBuf[1] & 0xffL) << 16
                | (lenBuf[2] & 0xffL) << 8 | (lenBuf[3] & 0xffL);
        if (u > MAX_LENGTH) {
            throw new IOException("Length exceeds maximum: " + u);
        }
        return (int) u;
    }

    /**
     * Reads the next message from the stream.
     *
     * @return the next {@link PropertyUpdateMessage}, or empty if EOF was reached
     * at the start of a new message (clean stream end)
     * @throws IOException if EOF is encountered in the middle of a message, or on I/O error
     */
    @NotNull
    public Optional<PropertyUpdateMessage> readMessage() throws IOException {
        int keyLen = readLength();
        if (keyLen < 0) {
            return Optional.empty();
        }

        byte[] keyBytes = readExactly(keyLen);
        String key = new String(keyBytes, StandardCharsets.UTF_8);

        int valueLen = readLength();
        if (valueLen < 0) {
            throw new IOException("Unexpected EOF after key (expected value length)");
        }

        byte[] value = readExactly(valueLen);

        return Optional.of(new PropertyUpdateMessage(key, value));
    }

    /**
     * @return bytes read (0 only on EOF before any byte)
     */
    private int readFully(byte[] buf, int count) throws IOException {
        int total = 0;
        while (total < count) {
            int n = in.read(buf, total, count - total);
            if (n < 0) {
                if (total == 0) {
                    return -1;
                }
                throw new EOFException("Unexpected EOF (expected " + count + " bytes, got " + total + ")");
            }
            total += n;
        }
        return total;
    }

    private byte[] readExactly(int length) throws IOException {
        byte[] buf = new byte[length];
        readFully(buf, length);
        return buf;
    }
}
