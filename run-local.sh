#!/bin/bash

# run-local.sh - Запуск всех сервисов локально (без Docker)
echo "=== Order Platform - Local Run ==="

# Проверка Maven Wrapper
if [ ! -f "./mvnw" ]; then
    echo "Error: Maven Wrapper не найден. Выполните: mvn wrapper:wrapper"
    exit 1
fi

# Проверка Java
echo "Java version:"
java -version 2>&1 | head -1
echo ""

# Сборка проекта
echo "Building project..."
./mvnw clean install -DskipTests
if [ $? -ne 0 ]; then
    echo "Error: Сборка проекта не удалась!"
    exit 1
fi
echo "Project built successfully!"
echo ""

# Запуск сервисов
echo "=== Available Services ==="
echo "1. auth-service (port 8090)"
echo "2. api-gateway (port 8080)"
echo "3. user-service (port 8082)"
echo "4. product-service (port 8083)"
echo "5. inventory-service (port 8084)"
echo "6. order-service (port 8085)"
echo "7. notification-service (port 8086)"
echo ""
echo "Run individual service with:"
echo "  cd auth-service && ./../mvnw spring-boot:run"
echo ""
echo "Note: Infrastructure (PostgreSQL, Redis, Kafka) must be running!"
