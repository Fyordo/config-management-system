FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/*.jar cms-server.jar

ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "cms-server.jar"]