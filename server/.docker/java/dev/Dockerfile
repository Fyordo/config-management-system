FROM eclipse-temurin:21-jre

RUN apt-get update && \
    apt-get install -y --no-install-recommends librocksdb-dev && \
    rm -rf /var/lib/apt/lists/* \

WORKDIR /app

COPY build/libs/*.jar cms-server.jar

ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-Dspring.profiles.active=dev",  "-jar", "cms-server.jar"]