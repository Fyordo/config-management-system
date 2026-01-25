# Build stage
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
        ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /build

COPY CMakeLists.txt ./
COPY main.cpp ./
COPY grpc/ ./grpc/
COPY proto/ ./proto/

RUN mkdir -p build && \
    cd build && \
    cmake .. -G Ninja -DCMAKE_BUILD_TYPE=Release && \
    cmake --build . --config Release

# Runtime stage
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libgrpc++1 \
        libgrpc10 \
        libprotobuf23 \
        libstdc++6 \
        libgcc-s1 \
        ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN useradd -m -u 1000 cmsagent && \
    mkdir -p /app && \
    chown -R cmsagent:cmsagent /app

WORKDIR /app

# Copy binary from builder stage
COPY --from=builder /build/build/cmsagent /app/cmsagent

# Switch to non-root user
USER cmsagent

# Expose port (if needed for health checks, though agent is a client)
EXPOSE 50051

# Set entrypoint
ENTRYPOINT ["/app/cmsagent"]

# Default command (can be overridden in k8s deployment)
CMD []
