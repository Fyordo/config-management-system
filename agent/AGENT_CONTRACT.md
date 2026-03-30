# AGENT_CONTRACT.md — UNIX-socket protocol for Property updates

This contract describes how the agent (`agent/grpc/agent_channel_client.cpp`) sends configuration change information to a UNIX domain socket.

## High-level model

1. The agent connects to a UNIX socket at `CMS_UNIX_SOCKET_PATH` (field `config.unixSocketPath`).
2. For each update (and also during initial properties sync) the agent sends **a single message** and then closes the connection.
3. The message is transported over `AF_UNIX` + `SOCK_STREAM` (connection-oriented byte stream, similar to TCP but using a UNIX-domain address).

If `CMS_UNIX_SOCKET_PATH` is not set (empty string), the agent does not send anything to the socket.

## Message framing

Each message consists of:

1. `payload_len` — `uint32` in **big-endian** byte order (4 bytes), equal to the number of bytes in `payload`.
2. `payload` — bytes of a protobuf-serialized `com.fyordo.cms.Property` message.

Byte layout:

```
[ payload_len: uint32 BE ] [ payload: payload_len bytes ]
```

## Protobuf: Property

The `payload` is a protobuf message:

```proto
message Property {
  string key = 1;
  bytes value = 2;
}
```

Field semantics:

- `key` — configuration key (string).
- `value` — configuration value as **opaque bytes** (`bytes`). The agent does not require this to be valid UTF‑8.

## Receiver-side read order

The receiver must:

1. Read exactly 4 bytes of `payload_len` and interpret them as big-endian `uint32`.
2. Read exactly `payload_len` bytes of `payload`.
3. Decode `payload` as a protobuf `Property`.
4. Handle (e.g. store) the resulting `key` and `value`.
5. Close the connection (the agent itself closes the socket after sending).

There are no extra delimiters or fields besides `payload_len`.
The contract does not define any ACK/response from the receiver: the agent just writes the message and closes the connection.

## Error handling (agent side)

The agent:

- logs errors to `stderr` on failed `socket()/connect()/send()` calls and on `Property` serialization failure;
- closes the socket file descriptor when sending fails;
- does not retry sending the same update (subsequent updates arrive as separate events).

The agent also checks that the socket path length fits into `addr.sun_path`; if it does not, the write to the socket is skipped.