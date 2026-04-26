# dev-down.ps1
Write-Host "Stopping Order Platform..." -ForegroundColor Yellow

# Stop and remove containers, networks, volumes
docker-compose down -v

Write-Host "All services stopped and cleaned up" -ForegroundColor Green