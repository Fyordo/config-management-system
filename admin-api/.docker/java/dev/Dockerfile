FROM eclipse-temurin:21-jre

ARG TARGETARCH
ARG AP_VERSION=4.3

RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && \
    AP_ARCH=$([ "$TARGETARCH" = "arm64" ] && echo "arm64" || echo "x64") && \
    curl -fSL "https://github.com/async-profiler/async-profiler/releases/download/v${AP_VERSION}/async-profiler-${AP_VERSION}-linux-${AP_ARCH}.tar.gz" \
      | tar xz -C /opt && \
    mv /opt/async-profiler-* /opt/async-profiler && \
    apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

ENV PATH="/opt/async-profiler:${PATH}"

WORKDIR /app

COPY build/libs/*.jar cms-admin-api.jar

ENTRYPOINT ["java", \
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", \
    "-agentpath:/opt/async-profiler/lib/libasyncProfiler.so", \
    "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints", \
    "-Dspring.profiles.active=dev", \
    "-jar", "cms-admin-api.jar"]