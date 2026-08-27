FROM amazoncorretto:17-alpine
# Upgrade system packages to pick up security fixes (e.g. OpenSSL CVEs)
RUN apk upgrade --no-cache && apk add --no-cache curl bash
WORKDIR /app
COPY target/*.jar app.jar
COPY wait-for-it.sh /wait-for-it.sh
RUN sed -i 's/\r$//' /wait-for-it.sh && chmod +x /wait-for-it.sh
EXPOSE 8082
ENTRYPOINT ["/wait-for-it.sh", "user-db", "5432", "--timeout=30", "--", "java", "-jar", "app.jar"]
