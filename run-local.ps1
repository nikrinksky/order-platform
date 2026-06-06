# run-local.ps1 - Запуск всех сервисов локально (без Docker)
Write-Host "=== Order Platform - Local Run ===" -ForegroundColor Cyan
Write-Host ""

# Проверка Maven Wrapper
if (!(Test-Path "./mvnw.cmd")) {
    Write-Error "Maven Wrapper не найден. Выполните: mvn wrapper:wrapper"
    exit 1
}

# Проверка Java
$javaVersion = java -version 2>&1 | Select-String "version"
Write-Host "Java version: $javaVersion" -ForegroundColor Yellow
Write-Host ""

# Сборка проекта
Write-Host "Building project..." -ForegroundColor Yellow
& .\mvnw.cmd clean install -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Error "Сборка проекта не удалась!"
    exit 1
}
Write-Host "Project built successfully!" -ForegroundColor Green
Write-Host ""

# Запуск сервисов
Write-Host "=== Available Services ===" -ForegroundColor Cyan
Write-Host "1. auth-service (port 8090)" -ForegroundColor White
Write-Host "2. api-gateway (port 8080)" -ForegroundColor White
Write-Host "3. user-service (port 8082)" -ForegroundColor White
Write-Host "4. product-service (port 8083)" -ForegroundColor White
Write-Host "5. inventory-service (port 8084)" -ForegroundColor White
Write-Host "6. order-service (port 8085)" -ForegroundColor White
Write-Host "7. notification-service (port 8086)" -ForegroundColor White
Write-Host ""
Write-Host "Run individual service with:" -ForegroundColor Cyan
Write-Host "  cd auth-service; & ../mvnw.cmd spring-boot:run" -ForegroundColor White
Write-Host ""
Write-Host "Note: Infrastructure (PostgreSQL, Redis, Kafka) must be running!" -ForegroundColor Yellow
