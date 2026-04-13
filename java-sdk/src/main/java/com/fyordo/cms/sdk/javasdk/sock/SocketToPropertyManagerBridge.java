package com.fyordo.cms.sdk.javasdk.sock;

import com.fyordo.cms.sdk.javasdk.property.PropertyManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public final class SocketToPropertyManagerBridge {

    private final PropertyManager propertyManager;
    private final PropertyUpdateStreamReader reader;

    public SocketToPropertyManagerBridge(@NotNull PropertyManager propertyManager,
                                         @NotNull InputStream in) {
        this.propertyManager = Objects.requireNonNull(propertyManager);
        this.reader = new PropertyUpdateStreamReader(Objects.requireNonNull(in));
    }

    public void processStream() throws IOException {
        Optional<PropertyUpdateMessage> opt;
        while ((opt = reader.readMessage()).isPresent()) {
            PropertyUpdateMessage msg = opt.get();
            propertyManager.store(msg.getKey(), new String(msg.getValue(), StandardCharsets.UTF_8));
        }
    }
}
