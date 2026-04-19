# Go SDK

CMS SDK for Go. Stores configuration as `map[string]string` in memory, loads initial values from a JSON file and receives live updates from the CMS agent via a UNIX domain socket.

## Lifecycle

```
PropertyManager.Init()
  ├── ReadFromFile()   - parses JSON, normalizes all values to strings
  └── ListenSocket()   - starts a goroutine with a UNIX socket server
```

After `Init()`, properties are available via `Get(key)`. If the socket cannot be bound, the file-based config is still loaded - `Init()` returns `nil`.

## Socket protocol

The SDK acts as a **server** - it binds a `AF_UNIX` socket and waits for the agent to connect as a client.

Each message on the wire:

```
[4 bytes, big-endian] payload length
[N bytes]             protobuf-encoded Property { string key = 1; bytes value = 2; }
```

The `value` bytes are decoded as UTF-8 and stored as `string`. Max payload size is 1 MB.

Connections are processed sequentially - the next `Accept()` happens only after the current connection is fully read. This guarantees that updates arriving in order are applied in the same order.

## Usage

```go
repo := cms.NewInMemoryPropertyRepository()
pm := cms.NewPropertyManager(repo, "/app/config/application.json", "/app/config/cms.sock")

pm.AddUpdateCallback("feature.flag", func(key string, old, new *string) {
    log.Printf("property %q updated", key)
})

if err := pm.Init(); err != nil {
    log.Fatal(err)
}

value := pm.Get("feature.flag") // *string; nil if missing
```
