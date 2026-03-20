package com.fyordo.cms.sdk.javasdk.property;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fyordo.cms.sdk.javasdk.sock.SocketToPropertyManagerBridge;

public class PropertyManager {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, PropertyUpdateCallback> callbacks;
    private final PropertyRepository repository;
    private final PropertyUpdateCallback defaultCallback;
    private final Path configFilePath;
    private final Path unixSocketPath;

    private volatile Thread socketListenerThread;

    public PropertyManager(@NotNull PropertyRepository repository,
                           @NotNull String configFilePath,
                           @NotNull String unixSocketPath) {
        this(repository, configFilePath, unixSocketPath, (key, oldValue, newValue) -> {
        });
    }

    public PropertyManager(@NotNull PropertyRepository repository,
                           @NotNull String configFilePath,
                           @NotNull String unixSocketPath,
                           @NotNull PropertyUpdateCallback defaultCallback) {
        this.repository = Objects.requireNonNull(repository);
        this.callbacks = new HashMap<>();
        this.defaultCallback = Objects.requireNonNull(defaultCallback);
        this.configFilePath = Path.of(Objects.requireNonNull(configFilePath));
        this.unixSocketPath = Path.of(Objects.requireNonNull(unixSocketPath));
    }

    public void init() {
        waitForConfigFile();
        readFromFile();
        listenSocket();
    }

    private void waitForConfigFile() {
        int maxAttempts = 60;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (Files.exists(configFilePath)) {
                return;
            }
            System.out.printf("[PropertyManager] Config file not found, waiting... (%d/%d): %s%n",
                    attempt, maxAttempts, configFilePath);
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for config file: " + configFilePath, e);
            }
        }
        throw new IllegalArgumentException(
                "Config file did not appear within " + maxAttempts + " seconds: " + configFilePath);
    }

    public void readFromFile() {
        try {
            if (!Files.exists(configFilePath)) {
                throw new IllegalArgumentException("File doesn't exists");
            }

            String json = Files.readString(configFilePath, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                throw new IllegalArgumentException("File is blank");
            }

            Map<String, Object> values = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });

            if (values == null) {
                return;
            }

            for (Map.Entry<String, Object> entry : values.entrySet()) {
                store(entry.getKey(), entry.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read properties from file " + configFilePath, e);
        }
    }

    public void addUpdateCallback(@NotNull String key,
                                  @Nullable PropertyUpdateCallback callback) {
        callbacks.put(key, callback);
    }

    @Nullable
    public <T> T get(@NotNull String key) {
        return (T) repository.getByKey(key);
    }

    public void store(@NotNull String key,
                      @Nullable Object newValue) {
        Object oldValue = repository.store(key, newValue);
        callbacks.getOrDefault(key, defaultCallback)
                .apply(key, oldValue, newValue);
    }

    @NotNull
    public synchronized Thread listenSocket() {
        Thread existing = socketListenerThread;
        if (existing != null && existing.isAlive()) {
            return existing;
        }

        System.out.println("Starting socket-listening thread");
        Thread t = new Thread(() -> {
            try {
                Files.createDirectories(unixSocketPath.getParent() != null ? unixSocketPath.getParent() : Path.of("."));
                Files.deleteIfExists(unixSocketPath);

                try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                    server.bind(UnixDomainSocketAddress.of(unixSocketPath));
                    System.out.println("Listening socket...");
                    while (!Thread.currentThread().isInterrupted()) {
                        try (SocketChannel client = server.accept()) {
                            SocketToPropertyManagerBridge bridge = new SocketToPropertyManagerBridge(
                                    this,
                                    Channels.newInputStream(client)
                            );
                            bridge.processStream();
                        }
                    }
                } finally {
                    Files.deleteIfExists(unixSocketPath);
                }
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }, "property-manager-socket-listener");

        t.setDaemon(true);
        socketListenerThread = t;
        t.start();
        return t;
    }
}
