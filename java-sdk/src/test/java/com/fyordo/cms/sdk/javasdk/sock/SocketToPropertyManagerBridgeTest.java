package com.fyordo.cms.sdk.javasdk.sock;

import com.fyordo.cms.CmsProto;
import com.fyordo.cms.sdk.javasdk.property.PropertyManager;
import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepository;
import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepositoryImpl;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SocketToPropertyManagerBridgeTest {

    @Test
    void processStreamAppliesUpdatesToPropertyManager() throws IOException {
        PropertyRepository repository = new PropertyRepositoryImpl();
        PropertyManager manager = new PropertyManager(
                repository,
                Paths.get(System.getProperty("java.io.tmpdir"), "dummy.json").toString(),
                Paths.get(System.getProperty("java.io.tmpdir"), "dummy.sock").toString()
        );

        byte[] stream = buildStream(
                new PropertyUpdateMessage("key1", "val1".getBytes(StandardCharsets.UTF_8)),
                new PropertyUpdateMessage("key2", new byte[]{1, 2, 3})
        );

        SocketToPropertyManagerBridge bridge =
                new SocketToPropertyManagerBridge(manager, new ByteArrayInputStream(stream));

        assertNull(repository.getByKey("key1"));
        assertNull(repository.getByKey("key2"));

        bridge.processStream();

        Object v1 = repository.getByKey("key1");
        Object v2 = repository.getByKey("key2");

        assertArrayEquals("val1".getBytes(StandardCharsets.UTF_8), (byte[]) v1);
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) v2);
    }

    private static byte[] buildStream(PropertyUpdateMessage... messages) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (PropertyUpdateMessage msg : messages) {
            byte[] payload = CmsProto.Property.newBuilder()
                    .setKey(msg.getKey())
                    .setValue(ByteString.copyFrom(msg.getValue()))
                    .build()
                    .toByteArray();
            writeInt(out, payload.length);
            out.write(payload);
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

