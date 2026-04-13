package com.fyordo.cms.sdk.javasdk.sock;

import com.fyordo.cms.CmsProto;
import org.jetbrains.annotations.NotNull;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

public final class PropertyUpdateStreamReader {

    private static final int MAX_LENGTH = 1024 * 1024; // 1 MB

    private final InputStream in;
    private final byte[] lenBuf = new byte[4];

    public PropertyUpdateStreamReader(@NotNull InputStream in) {
        this.in = Objects.requireNonNull(in);
    }

    private int readLength() throws IOException {
        int n = readFully(lenBuf, 4);
        if (n < 0) {
            return -1;
        }
        long u = (lenBuf[0] & 0xffL) << 24 | (lenBuf[1] & 0xffL) << 16
                | (lenBuf[2] & 0xffL) << 8 | (lenBuf[3] & 0xffL);
        if (u > MAX_LENGTH) {
            throw new IOException("Length exceeds maximum: " + u);
        }
        return (int) u;
    }

    @NotNull
    public Optional<PropertyUpdateMessage> readMessage() throws IOException {
        int payloadLen = readLength();
        if (payloadLen < 0) {
            return Optional.empty();
        }

        byte[] payload = readExactly(payloadLen);

        CmsProto.Property property;
        try {
            property = CmsProto.Property.parseFrom(payload);
        } catch (IOException e) {
            throw new IOException("Failed to parse protobuf Property payload", e);
        }

        return Optional.of(new PropertyUpdateMessage(
                property.getKey(),
                property.getValue().toByteArray()
        ));
    }

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
