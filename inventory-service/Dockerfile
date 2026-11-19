# syntax=docker/dockerfile:1

# ---- Build stage ------------------------------------------------------------
# The build context is the repository root; see auth-service/Dockerfile for why.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
# The script repeats the build, so a truncated download from the Maven mirror does not fail the
# image; see its header for the details.
RUN --mount=type=cache,target=/root/.m2 \
    sh /workspace/.mvn/docker-build.sh inventory-service

# ---- Runtime stage ----------------------------------------------------------
FROM amazoncorretto:17-alpine
# Upgrade system packages to pick up security fixes (e.g. OpenSSL CVEs)
RUN apk upgrade --no-cache
WORKDIR /app
COPY --from=build /workspace/inventory-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
