# Admin Module (Optional)

React + Vite web UI for managing properties, viewing audit entries, and checking Raft status.

> This module is **optional**.  
> Core config delivery works without it; you can use [Server API](../server/openapi.yaml) directly.

## What It Does

- Browse and filter properties.
- Create, edit, copy, delete properties.
- View audit history and rollback from audit entries.
- Inspect Raft cluster status.

## Environment

Create `.env` from `.env.example` and set:

- `VITE_API_URL` — Admin API base URL.
- `VITE_SERVER_URL` — direct Server API URL (used for property operations).

## Run Locally

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
```

## Lint

```bash
npm run lint
```
