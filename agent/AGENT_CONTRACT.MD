## Agent UNIX socket contract

This document describes the data format the agent sends to a UNIX socket at the path specified by the `CMS_UNIX_SOCKET_PATH` environment variable (for example `/app/cms.sock`).

### Transport

- **Socket type**: UNIX domain socket (`AF_UNIX`, `SOCK_STREAM`).
- **Direction**: the agent is a **client** and connects to an already listening server socket.
- **Framing**: the stream consists of a sequence of self-delimiting messages, one message per property update.

### Message format

Each message has the following binary layout:

1. `key_len` — 4 bytes, unsigned 32-bit integer, **network byte order** (big‑endian).
2. `key` — `key_len` bytes, the property key.
3. `value_len` — 4 bytes, unsigned 32-bit integer, **network byte order** (big‑endian).
4. `value` — `value_len` bytes, the property value.

There is **no delimiter** between messages besides this length-prefix framing. Messages follow one after another in the byte stream.

### Semantics

- **Key**:
  - Type: UTF‑8 string.
  - Content: property key from the CMS server (e.g. `"my.config.key"`).

- **Value**:
  - Type: opaque byte sequence.
  - Source: `value` field from the gRPC `ServerPropertyUpdateEvent.property`.
  - Consumers **must not assume** UTF‑8 or any particular encoding; they should treat it as arbitrary binary data unless they have out‑of‑band knowledge of the encoding.

### Reading algorithm (pseudocode)

```text
loop:
  read 4 bytes -> key_len_be
  if EOF: stop
  key_len = ntohl(key_len_be)

  read key_len bytes -> key

  read 4 bytes -> value_len_be
  value_len = ntohl(value_len_be)

  read value_len bytes -> value

  handle_property_update(key, value)
```

If any of the `read` operations return EOF before the requested number of bytes is read, the consumer should treat this as a broken or closed stream.

