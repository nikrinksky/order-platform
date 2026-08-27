FROM amazoncorretto:17-alpine
# Upgrade system packages to pick up security fixes (e.g. OpenSSL CVEs)
RUN apk upgrade --no-cache
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
