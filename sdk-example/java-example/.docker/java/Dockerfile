FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/*.jar cms-java-example.jar

ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "cms-java-example.jar"]