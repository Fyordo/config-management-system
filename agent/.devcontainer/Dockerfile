# syntax=docker/dockerfile:1

ARG VARIANT="ubuntu-22.04"
FROM mcr.microsoft.com/devcontainers/cpp:${VARIANT}

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        build-essential \
        clang \
        clang-format \
        cmake \
        ninja-build \
        pkg-config \
        protobuf-compiler \
        protobuf-compiler-grpc \
        libprotobuf-dev \
        libprotoc-dev \
        libgrpc++-dev \
        libgrpc-dev \
        grpc-proto && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

