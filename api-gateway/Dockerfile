FROM amazoncorretto:17-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-Dspring.cloud.compatibility-verifier.enabled=false", "-jar", "app.jar"]
