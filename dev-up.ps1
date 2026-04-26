# dev-up.ps1
Write-Host "Starting Order Platform..." -ForegroundColor Cyan

# Check Docker
docker --version | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker is not running"
    exit 1
}

# Check if docker-compose.yml exists
if (-not (Test-Path "docker-compose.yaml")) {
    Write-Error "docker-compose.yaml not found"
    exit 1
}

# Start services
Write-Host "Starting Docker Compose..." -ForegroundColor Yellow
docker-compose up -d

# Wait for services
Write-Host "Waiting for services to be ready (30 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30

# Show status
Write-Host ""
Write-Host "Platform is ready!" -ForegroundColor Green
Write-Host ""
Write-Host "Access Points:" -ForegroundColor Cyan
Write-Host "  API Gateway: http://localhost:8080"
Write-Host "  Auth Service: http://localhost:8081"
Write-Host "  PostgreSQL: localhost:5432"
Write-Host "  MongoDB: localhost:27017"
Write-Host "  Redis: localhost:6379"
Write-Host "  Kafka: localhost:9092"
Write-Host "  Schema Registry: http://localhost:8081"
Write-Host "  Grafana: http://localhost:3000 (user: admin, pass: admin)"
Write-Host "  Jaeger: http://localhost:16686"
Write-Host "  MailHog: http://localhost:8025"
Write-Host ""
Write-Host "To stop: ./dev-down.ps1" -ForegroundColor Yellow

# Show running containers
Write-Host ""
Write-Host "Running containers:" -ForegroundColor Cyan
docker-compose ps