# run-all-local.ps1 - Запуск всех сервисов локально
Write-Host "=== Order Platform - Local Run All Services ===" -ForegroundColor Cyan
Write-Host ""

# Проверка Maven Wrapper
if (!(Test-Path "./mvnw.cmd")) {
    Write-Error "Maven Wrapper не найден. Выполните: mvn wrapper:wrapper"
    exit 1
}

# Установка переменных окружения для текущей сессии
Write-Host "Установка переменных окружения..." -ForegroundColor Yellow
$env:JWT_SECRET = "devSecretKeyForLocalDevelopmentOnlyDoNotUseInProduction1234567890"
$env:AUTH_SERVICE_URL = "http://host.docker.internal:8090"
Write-Host "JWT_SECRET и AUTH_SERVICE_URL установлены" -ForegroundColor Green
Write-Host ""

# Запуск сервисов в отдельных окнах
Write-Host "Запуск сервисов в отдельных окнах PowerShell..." -ForegroundColor Yellow
Write-Host ""

# Создаем окна для каждого сервиса
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:JWT_SECRET='devSecretKeyForLocalDevelopmentOnlyDoNotUseInProduction1234567890'; cd '$PWD'; Write-Host '=== auth-service ===' -ForegroundColor Cyan; .\mvnw.cmd spring-boot:run -pl auth-service"

Start-Sleep -Seconds 2

Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:AUTH_SERVICE_URL='http://host.docker.internal:8090'; cd '$PWD'; Write-Host '=== api-gateway ===' -ForegroundColor Cyan; .\mvnw.cmd spring-boot:run -pl api-gateway"

Start-Sleep -Seconds 2

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD'; Write-Host '=== user-service ===' -ForegroundColor Cyan; .\mvnw.cmd spring-boot:run -pl user-service"

Write-Host ""
Write-Host "=== Services started ===" -ForegroundColor Green
Write-Host "API Gateway: http://localhost:8080" -ForegroundColor White
Write-Host "Auth Service: http://localhost:8090" -ForegroundColor White
Write-Host "User Service: http://localhost:8082" -ForegroundColor White
Write-Host ""
Write-Host "Для остановки закройте открытые окна PowerShell" -ForegroundColor Yellow
Write-Host "Инфраструктура (PostgreSQL, Redis, Kafka) должна быть запущена через Docker!" -ForegroundColor Yellow
