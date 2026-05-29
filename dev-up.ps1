# dev-up.ps1
Write-Host "Starting Order Platform..." -ForegroundColor Cyan

# Check Docker
docker --version | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker is not running"
    exit 1
}

# Start infrastructure
Write-Host "Starting infrastructure with Docker Compose..." -ForegroundColor Yellow
docker-compose --profile dev up -d

# Wait for services
Write-Host "Waiting for services to be ready (15 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

Write-Host ""
Write-Host "Infrastructure is ready!" -ForegroundColor Green
Write-Host ""
Write-Host "Access Points:" -ForegroundColor Cyan
Write-Host "  Auth Service (Docker): http://localhost:8090"
Write-Host "  PostgreSQL: localhost:5432 (platform/dev123)"
Write-Host "  MongoDB: localhost:27017 (admin/dev123)"
Write-Host "  Redis: localhost:6379"
Write-Host ""
Write-Host "Development Tools:" -ForegroundColor Cyan
Write-Host "  pgAdmin: http://localhost:5050 (admin@orderplatform.com/admin)"
Write-Host "  mongo-express: http://localhost:8087 (admin/admin)"
Write-Host "  redis-commander: http://localhost:8088"
Write-Host ""
Write-Host "Swagger Documentation (after starting auth-service in IDEA):" -ForegroundColor Yellow
Write-Host "  http://localhost:8090/swagger-ui.html"
Write-Host "  http://localhost:8090/api-docs"
Write-Host ""
Write-Host "Note: API Gateway should be started manually in IDEA (port 8080)" -ForegroundColor Yellow
Write-Host ""
Write-Host "To stop: ./dev-down.ps1" -ForegroundColor Yellow

# Show running containers
Write-Host ""
Write-Host "Running containers:" -ForegroundColor Cyan
docker-compose ps