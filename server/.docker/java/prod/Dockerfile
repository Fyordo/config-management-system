FROM eclipse-temurin:21-jre

RUN apt-get update && \
    apt-get install -y --no-install-recommends librocksdb-dev && \
    rm -rf /var/lib/apt/lists/* \

WORKDIR /app

COPY build/libs/*.jar cms-server.jar

ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "cms-server.jar"]