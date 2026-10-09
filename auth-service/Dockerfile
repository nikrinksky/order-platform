# syntax=docker/dockerfile:1

# ---- Build stage ------------------------------------------------------------
# The build context is the repository root: this service depends on the sibling modules
# common-security, common-events and outbox-spring-boot-starter, so the whole reactor has to be
# visible. Building the jar here is what makes "docker compose build" work on a clean clone -
# the previous Dockerfile expected a pre-built target/*.jar and failed with "lstat /target".
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -s /workspace/.mvn/docker-settings.xml -pl auth-service -am -DskipTests package

# ---- Runtime stage ----------------------------------------------------------
FROM amazoncorretto:17-alpine
# Upgrade system packages to pick up security fixes (e.g. OpenSSL CVEs)
RUN apk upgrade --no-cache && apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/auth-service/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]