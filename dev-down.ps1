# dev-down.ps1
Write-Host "Stopping Order Platform..." -ForegroundColor Yellow

# Stop infrastructure
docker-compose --profile dev down -v

Write-Host "All services stopped and cleaned up" -ForegroundColor Green