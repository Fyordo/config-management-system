# ── Build stage ─────────────────────────────────────────────────────────────
FROM ubuntu:22.04 AS builder

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        build-essential \
        cmake \
        ninja-build \
        pkg-config \
        protobuf-compiler \
        protobuf-compiler-grpc \
        libprotobuf-dev \
        libprotoc-dev \
        libgrpc++-dev \
        libgrpc-dev \
        grpc-proto \
        nlohmann-json3-dev \
        ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /build

COPY CMakeLists.txt ./
COPY main.cpp ./
COPY grpc/ ./grpc/
COPY proto/ ./proto/

RUN mkdir -p build && \
    cd build && \
    cmake .. -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_CXX_FLAGS_RELEASE="-Os -DNDEBUG -ffunction-sections -fdata-sections -fvisibility=hidden" \
        -DCMAKE_EXE_LINKER_FLAGS="-Wl,--gc-sections -Wl,--as-needed" && \
    cmake --build . && \
    strip --strip-all cmsagent

# ── Runtime stage ────────────────────────────────────────────────────────────
FROM alpine:3.21

RUN apk add --no-cache \
        libstdc++ \
        grpc \
        libprotobuf \
        ca-certificates && \
    adduser -D -u 1000 cmsagent

WORKDIR /app

COPY --from=builder /build/build/cmsagent /app/cmsagent

RUN chown cmsagent:cmsagent /app/cmsagent

USER cmsagent

EXPOSE 50051

ENTRYPOINT ["/app/cmsagent"]

CMD []
