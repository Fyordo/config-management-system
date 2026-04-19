# Python SDK

CMS SDK for Python. Stores configuration as `dict[str, str]` in memory, loads initial values from a JSON file and receives live updates from the CMS agent via a UNIX domain socket. Zero runtime dependencies - stdlib only.

## Lifecycle

```
PropertyManager.init()
  ├── _read_from_file()    - parses JSON, normalizes all values to strings
  └── _start_listener()    - starts a daemon thread with a UNIX socket server
```

After `init()`, properties are available via `get(key)`. If the socket cannot be bound, the file-based config is still loaded.

## Socket protocol

The SDK acts as a **server** - it binds a `AF_UNIX` socket and waits for the agent to connect as a client.

Each message on the wire:

```
[4 bytes, big-endian] payload length
[N bytes]             protobuf-encoded Property { string key = 1; bytes value = 2; }
```

The `value` bytes are decoded as UTF-8 and stored as `str`. Max payload size is 1 MB.

Connections are processed sequentially - the next `accept()` happens only after the current connection is fully read. This guarantees that updates arriving in order are applied in the same order.

## Usage

```python
from cms import PropertyManager

pm = PropertyManager(
    config_file_path="/app/config/application.json",
    unix_socket_path="/app/config/cms.sock",
)

pm.add_update_callback("feature.flag", lambda key, old, new: print(f"{key}: {old} -> {new}"))
pm.init()

value = pm.get("feature.flag")
```
