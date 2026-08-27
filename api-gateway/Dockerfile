FROM amazoncorretto:17-alpine
# Upgrade system packages to pick up security fixes (e.g. OpenSSL CVEs)
RUN apk upgrade --no-cache
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.cloud.compatibility-verifier.enabled=false", "-jar", "app.jar"]
