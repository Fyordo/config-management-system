# Java SDK

CMS SDK for Java. Stores configuration as `Map<String, String>` in memory, loads initial values from a JSON file and receives live updates from the CMS agent via a UNIX domain socket.

## Lifecycle

```
PropertyManager.init()
  ├── waitForConfigFile()   — polls up to 60 s for the JSON file to appear
  ├── readFromFile()        — parses JSON, stores each key/value as String
  └── listenSocket()        — starts a daemon thread with a UNIX socket server
```

After `init()`, properties are available via `get(key)`. If the socket cannot be bound, the file-based config is still loaded.

## Socket protocol

The SDK acts as a **server** — it binds a `AF_UNIX` socket and waits for the agent to connect as a client.

Each message on the wire:

```
[4 bytes, big-endian] payload length
[N bytes]             protobuf-encoded Property { string key = 1; bytes value = 2; }
```

The `value` bytes are decoded as UTF-8 and stored as `String`. Max payload size is 1 MB.

Connections are processed sequentially — the next `accept()` happens only after the current connection is fully read. This guarantees that updates arriving in order are applied in the same order.

## Usage

```java
PropertyRepository repo = new PropertyRepositoryImpl();
PropertyManager pm = new PropertyManager(repo, "/app/config/application.json", "/app/config/cms.sock");

pm.addUpdateCallback("feature.flag", (key, oldVal, newVal) -> {
    System.out.println(key + ": " + oldVal + " -> " + newVal);
});

pm.init();

String value = pm.get("feature.flag");
```
