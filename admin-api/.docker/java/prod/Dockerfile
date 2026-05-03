FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/*.jar cms-admin-api.jar

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-Dspring.profiles.active=prod", \
    "-jar", \
    "cms-admin-api.jar"]