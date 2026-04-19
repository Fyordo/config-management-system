# Admin API Module (Optional)

Spring Boot service for Admin UI backend: audit storage/query and cluster metadata/status aggregation.

> This module is **optional**.  
> Core config delivery (`server` + `agent`) works without `admin-api`.

## What It Does

- Stores and serves audit entries.
- Provides cluster list and cluster status for Admin UI.
- Exposes REST endpoints under `/api/v1`.

## REST Endpoints

- `GET /api/v1/audit` — search audit entries (paged).
- `GET /api/v1/audit/{id}` — get full audit entry.
- `POST /api/v1/audit` — create audit entry.
- `GET /api/v1/cluster/names` — list configured clusters.
- `GET /api/v1/cluster/status` — get cluster statuses.

## Database

You can use **any relational database** supported by JDBC + Hibernate dialect.

Current configuration is provided via environment variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `DB_DRIVER_CLASS_NAME`
- `DB_DIALECT`

## Run Locally

```bash
./gradlew bootRun
```

## Tests

```bash
./gradlew test
```
