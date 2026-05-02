package com.fyordo.cms.sdk.javasdk.sock;

import com.fyordo.cms.CmsProto;
import com.fyordo.cms.sdk.javasdk.property.PropertyManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SocketToPropertyManagerBridge {
    private static final Logger LOG = Logger.getLogger(PropertyManager.class.getName());

    private final PropertyManager propertyManager;
    private final PropertyUpdateStreamReader reader;

    public SocketToPropertyManagerBridge(@NotNull PropertyManager propertyManager,
                                         @NotNull InputStream in) {
        this.propertyManager = Objects.requireNonNull(propertyManager);
        this.reader = new PropertyUpdateStreamReader(Objects.requireNonNull(in));
    }

    public void processStream() throws IOException {
        Optional<CmsProto.Property> opt;
        while ((opt = reader.readMessage()).isPresent()) {
            CmsProto.Property msg = opt.get();
            propertyManager.store(msg.getKey(), new String(msg.getValue().toByteArray(), StandardCharsets.UTF_8));
            LOG.log(Level.INFO, MessageFormat.format(
                    "Applied property [{0}] for {1}ms",
                    msg.getKey(),
                    System.currentTimeMillis() - msg.getModifiedMs()
            ));
        }
    }
}
