# Config Management System

Distributed open-source configuration management platform for delivering runtime config updates from a Raft-backed server to applications with low latency and recovery guarantees.

## Purpose

This project provides:

- centralized config storage with consensus (Raft),
- near real-time delivery of updates to runtime agents,
- local propagation from agent to SDK/client process,
- optional admin tooling for audit and cluster visibility.

## Delivery Model (Hybrid Push-Poll)

The system uses a **hybrid push-poll model** to deliver data to the final program:

- **Push path:** server pushes committed updates to agent over gRPC stream; agent forwards updates to local consumers.
- **Poll/verification path:** agent periodically sends revision ACKs; if revision drift is detected, server re-sends an init snapshot.

This combines low-latency update propagation with self-healing consistency checks.

## Main Components

- `server` — Raft-backed config server with HTTP + gRPC APIs.
- `agent` — C++ runtime agent that receives updates and writes/forwards them locally.
- `admin` *(optional)* — web UI for properties, audit, and cluster status.
- `admin-api` *(optional)* — backend for admin UI (audit + cluster metadata/status).
- `java-sdk`, `python-sdk`, `go-sdk` — SDKs for consuming local agent updates.

## Module Documentation

- [Server README](server/README.md)
- [Agent README](agent/README.md)
- [Admin README](admin/README.md)
- [Admin API README](admin-api/README.md)
- [Java SDK README](java-sdk/README.md)
- [Python SDK README](python-sdk/README.md)
- [Go SDK README](go-sdk/README.md)

## API Specs

- [Server OpenAPI](server/openapi.yaml)
- [Admin API OpenAPI](admin-api/openapi.yaml)

## E2E Tests

### How to run?
1. Start necessary docker containers: `e2e/docker`
2. Start nginx proxy with [config](e2e/docker/server/nginx-cms-raft-http.conf) for raft nodes
3. Configure (if needed) ip and port in [Makefile](e2e/Makefile)
4. Run
```bash
cd e2e
make run-e2e # Runs all e2e tests
make kill-e2e # Kills e2e-tests scripts (if present)
make analyze # Runs python script with plots building and md-file generation
make cleanup # Cleans raft-nodes from test values
```

### Reports
- [v1.0.0-rc results](e2e/_results/v_1_0_0_rc/analysis_summary.md)
- [v1.0.0-beta3 results](e2e/_results/v_1_0_0_beta3/analysis_summary.md)
- [v1.0.0 results](e2e/_results/v_1_0_0/analysis_summary.md)
