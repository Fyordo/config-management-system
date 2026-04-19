# Server Module

Spring Boot + Kotlin service that stores config in a Raft cluster and streams committed updates to C++ agents over gRPC.

## What It Does

- Accepts property writes/deletes over HTTP.
- Replicates commands through Apache Ratis (Raft).
- Serves property reads from in-memory state.
- Pushes committed updates to connected agents via bidirectional gRPC stream.
- Tracks agent revision ACKs and re-sends init snapshot when agent is behind.

## Main Flow

1. Client sends `PUT/DELETE` HTTP request.
2. Server writes command to Raft.
3. After commit, state machine updates in-memory storage.
4. Update is published to agent stream (`watchProperties`).
5. Agent applies config and periodically sends ACK revision.

## HTTP API

- `POST /v1/property/modify/put`
- `POST /v1/property/modify/delete`
- `POST /v1/property/query`
- `POST /v1/property/query/get`
- `GET /v1/property/query/constants`
- `GET /raft/status/local`
- `GET /raft/status`

## gRPC API

- Service: `AgentChannelService`
- Stream method: `watchProperties` (agent connect + ACK, server init + updates)

## Key Environment Variables

- `HTTP_PORT` (default `8080`)
- `GRPC_PORT` (default `9090`)
- `RAFT_NODE_ID`
- `RAFT_NODE_HOST`
- `RAFT_NODE_PORT` (default `6000`)
- `RAFT_GROUP`
- `RAFT_PEERS`
- `MAX_VALUE_BYTES` (default `1048576`)
- `CORS_URLS`

## Observability

- Health + Prometheus endpoint exposure is configured in `application.properties`.
- Main metrics include property query/get timings and agent connection counters.
