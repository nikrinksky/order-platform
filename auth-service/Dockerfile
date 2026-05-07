FROM amazoncorretto:17-alpine
WORKDIR /app
COPY target/auth-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]