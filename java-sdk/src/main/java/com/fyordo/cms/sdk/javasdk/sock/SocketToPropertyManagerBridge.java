package com.fyordo.cms.sdk.javasdk.sock;

import com.fyordo.cms.sdk.javasdk.property.PropertyManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads property update messages from a stream (e.g. UNIX socket from the agent)
 * and applies each update via {@link PropertyManager#store(String, String)}.
 * Stream format is as in AGENT_CONTRACT.MD.
 */
public final class SocketToPropertyManagerBridge {

    private final PropertyManager propertyManager;
    private final PropertyUpdateStreamReader reader;

    public SocketToPropertyManagerBridge(@NotNull PropertyManager propertyManager,
                                         @NotNull InputStream in) {
        this.propertyManager = Objects.requireNonNull(propertyManager);
        this.reader = new PropertyUpdateStreamReader(Objects.requireNonNull(in));
    }

    /**
     * Reads messages from the stream until EOF and calls
     * {@link PropertyManager#store(String, String)} for each message.
     * Value is passed as {@code byte[]} (opaque per contract).
     *
     * @throws IOException if the stream ends in the middle of a message or on I/O error
     */
    public void processStream() throws IOException {
        Optional<PropertyUpdateMessage> opt;
        while ((opt = reader.readMessage()).isPresent()) {
            PropertyUpdateMessage msg = opt.get();
            propertyManager.store(msg.getKey(), new String(msg.getValue(), StandardCharsets.UTF_8));
        }
    }
}
