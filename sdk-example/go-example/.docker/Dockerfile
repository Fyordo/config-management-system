# ── Build stage ─────────────────────────────────────────────────────────────
# Build context must be the repository root:
#   docker build -f sdk-example/go-example/.docker/Dockerfile .
FROM golang:1.25-alpine AS builder

RUN apk add --no-cache git

WORKDIR /build

# Mirror the repository layout so the replace directive in go.mod
# (replace go-sdk => ../../go-sdk) resolves correctly:
#   /build/sdk-example/go-example/../../go-sdk  →  /build/go-sdk  ✓
COPY go-sdk/ ./go-sdk/
COPY sdk-example/go-example/ ./sdk-example/go-example/

WORKDIR /build/sdk-example/go-example

RUN go mod download

RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -ldflags="-s -w" -o /bin/go-example .

# ── Runtime stage ────────────────────────────────────────────────────────────
FROM alpine:3.21

RUN apk add --no-cache ca-certificates && \
    adduser -D -u 1000 appuser

COPY --from=builder /bin/go-example /app/go-example

RUN chown appuser:appuser /app/go-example

USER appuser

# In Kubernetes, supply these via env vars / ConfigMap / volume mounts:
#   CMS_CONFIG_PATH      - path to the JSON config file (mount a ConfigMap here)
#   CMS_UNIX_SOCKET_PATH - path to the UNIX socket (shared emptyDir with the agent)
#   HTTP_ADDR            - listen address (default :8080)
EXPOSE 8080

ENTRYPOINT ["/app/go-example"]
