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

RUN useradd -m -u 1000 cmsagent && \
    mkdir -p /app && \
    chown -R cmsagent:cmsagent /app

WORKDIR /app

COPY --from=builder /build/build/cmsagent /app/cmsagent

USER cmsagent

EXPOSE 50051

ENTRYPOINT ["/app/cmsagent"]

CMD []
