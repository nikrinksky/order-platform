FROM amazoncorretto:17-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]