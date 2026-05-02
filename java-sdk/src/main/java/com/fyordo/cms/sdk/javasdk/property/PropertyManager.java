package com.fyordo.cms.sdk.javasdk.property;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fyordo.cms.sdk.javasdk.property.repo.PropertyRepository;
import com.fyordo.cms.sdk.javasdk.sock.SocketToPropertyManagerBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PropertyManager {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(PropertyManager.class.getName());

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
        this.callbacks = new ConcurrentHashMap<>();
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
            LOG.info(String.format("Config file not found, waiting... (%d/%d): %s",
                    attempt, maxAttempts, configFilePath));
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

            Map<String, String> values = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });

            if (values == null) {
                return;
            }

            for (Map.Entry<String, String> entry : values.entrySet()) {
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
    public String get(@NotNull String key) {
        return repository.getByKey(key);
    }

    public void store(@NotNull String key,
                      @Nullable String newValue) {
        String oldValue = repository.store(key, newValue);
        try {
            callbacks.getOrDefault(key, defaultCallback)
                    .apply(key, oldValue, newValue);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "cms: callback threw for key '" + key + "'", e);
        }
    }

    @NotNull
    public synchronized Thread listenSocket() {
        Thread existing = socketListenerThread;
        if (existing != null && existing.isAlive()) {
            return existing;
        }

        LOG.info("Starting socket-listening virtual thread");
        Thread t = Thread.ofPlatform()
                .name("property-manager-socket-listener")
                .unstarted(() -> {
                    try {
                        Files.createDirectories(unixSocketPath.getParent() != null ? unixSocketPath.getParent() : Path.of("."));
                        Files.deleteIfExists(unixSocketPath);

                        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                            server.bind(UnixDomainSocketAddress.of(unixSocketPath));
                            LOG.info("Listening socket...");
                            while (!Thread.currentThread().isInterrupted()) {
                                try (SocketChannel client = server.accept()) {
                                    SocketToPropertyManagerBridge bridge = new SocketToPropertyManagerBridge(
                                            this,
                                            Channels.newInputStream(client)
                                    );
                                    bridge.processStream();
                                } catch (IOException e) {
                                    LOG.log(Level.WARNING, "cms: error processing connection", e);
                                }
                            }
                        } finally {
                            Files.deleteIfExists(unixSocketPath);
                        }
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "cms: socket listener failed", e);
                    }
                });

        socketListenerThread = t;
        t.start();
        return t;
    }
}
