# ── Build stage ─────────────────────────────────────────────────────────────
FROM alpine:3.21 AS builder

RUN apk add --no-cache \
        build-base \
        cmake \
        ninja \
        pkgconf \
        grpc-dev \
        protobuf-dev \
        nlohmann-json

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

# Collect exactly the runtime .so files the binary needs (via ldd), with symlinks
RUN mkdir /runtime-libs && \
    ldd /build/build/cmsagent | awk '/=>/{print $3}' | \
    grep -Ev '(libstdc\+\+|libgcc_s|libc\.musl|ld-musl)' | \
    while read -r lib; do \
        [ -f "$lib" ] || continue; \
        real=$(readlink -f "$lib"); \
        rname=$(basename "$real"); \
        lname=$(basename "$lib"); \
        cp "$real" "/runtime-libs/$rname"; \
        [ "$lname" != "$rname" ] && ln -sf "$rname" "/runtime-libs/$lname" || true; \
    done

# ── Runtime stage ────────────────────────────────────────────────────────────
FROM alpine:3.21

RUN apk add --no-cache libstdc++ ca-certificates && \
    adduser -D -u 1000 cmsagent

COPY --from=builder /runtime-libs/ /usr/lib/
COPY --from=builder /build/build/cmsagent /app/cmsagent

RUN chown cmsagent:cmsagent /app/cmsagent

USER cmsagent

EXPOSE 50051

ENTRYPOINT ["/app/cmsagent"]

CMD []
