# syntax=docker/dockerfile:1

# ---- Build stage ------------------------------------------------------------
# The build context is the repository root; see auth-service/Dockerfile for why.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -s /workspace/.mvn/docker-settings.xml -pl notification-service -am -DskipTests package

# ---- Runtime stage ----------------------------------------------------------
FROM amazoncorretto:17-alpine
# Upgrade system packages to pick up security fixes (e.g. OpenSSL CVEs)
RUN apk upgrade --no-cache
WORKDIR /app
COPY --from=build /workspace/notification-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
