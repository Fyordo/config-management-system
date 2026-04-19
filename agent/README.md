# Agent Module

C++ gRPC client that receives committed config updates from `server`, writes them atomically to a local file, and optionally forwards each update to a UNIX socket consumer.

## What It Does

- Opens bidirectional stream `watchProperties` to server gRPC endpoint.
- Sends `connect` event with agent identity (`namespace/service/appId`).
- Applies `init` snapshot and incremental `update` events.
- Persists current revision to disk and sends periodic ACKs (every 10s).
- Reconnects automatically when connection is lost.

## Required Environment Variables

- `CMS_NAMESPACE`
- `CMS_SERVICE`
- `CMS_APPID`
- `CMS_SERVER_HOST` (example: `localhost:9090`)
- `CMS_PROPERTIES_FILE` - JSON file for local config mirror.
- `CMS_REVISION_FILE` - file with last applied revision.
- `CMS_UNIX_SOCKET_PATH` - UNIX socket path for pushing updates.

## Build

```bash
mkdir -p build
cd build
cmake ..
cmake --build .
```

## Run

```bash
./cmsagent
```

## Runtime Notes

- Graceful shutdown on `SIGINT` / `SIGTERM`.
- If `CMS_UNIX_SOCKET_PATH` is set, each update is sent as a length-prefixed protobuf message.
- UNIX-socket wire format is documented in [AGENT_CONTRACT.md](AGENT_CONTRACT.md).