# test-auth.ps1 - Полный тест auth-service
Write-Host "=== Testing Auth Service ===" -ForegroundColor Cyan
Write-Host ""

# 1. Register
Write-Host "1. Registering new user..." -ForegroundColor Yellow
$randomNum = Get-Random -Minimum 1000 -Maximum 9999
$registerBody = @{
    email = "user$randomNum@example.com"
    password = "password123"
    firstName = "Test"
    lastName = "User$randomNum"
} | ConvertTo-Json

try {
    $registerResponse = Invoke-RestMethod -Uri "http://localhost:8090/api/auth/register" `
        -Method Post `
        -ContentType "application/json" `
        -Body $registerBody
    Write-Host "   User registered: $($registerResponse.email)" -ForegroundColor Green
} catch {
    Write-Host "   Registration failed" -ForegroundColor Red
    Write-Host $_.Exception.Message
    exit 1
}

# 2. Login
Write-Host ""
Write-Host "2. Logging in..." -ForegroundColor Yellow
$loginBody = @{
    email = "user$randomNum@example.com"
    password = "password123"
} | ConvertTo-Json

try {
    $loginResponse = Invoke-RestMethod -Uri "http://localhost:8090/api/auth/login" `
        -Method Post `
        -ContentType "application/json" `
        -Body $loginBody
    Write-Host "   Login successful" -ForegroundColor Green
    Write-Host "   Access Token: $($loginResponse.accessToken.Substring(0, 50))..." -ForegroundColor Gray
} catch {
    Write-Host "   Login failed" -ForegroundColor Red
    Write-Host $_.Exception.Message
    exit 1
}

# 3. Get current user
Write-Host ""
Write-Host "3. Getting current user info..." -ForegroundColor Yellow
$headers = @{
    "Authorization" = "Bearer $($loginResponse.accessToken)"
}

try {
    $meResponse = Invoke-RestMethod -Uri "http://localhost:8090/api/auth/me" `
        -Method Get `
        -Headers $headers
    Write-Host "   User info retrieved: $($meResponse.email)" -ForegroundColor Green
    Write-Host "   Name: $($meResponse.firstName) $($meResponse.lastName)" -ForegroundColor Gray
    Write-Host "   Roles: $($meResponse.roles -join ', ')" -ForegroundColor Gray
} catch {
    Write-Host "   Failed to get user info" -ForegroundColor Red
}

# 4. Refresh token
Write-Host ""
Write-Host "4. Refreshing token..." -ForegroundColor Yellow
$refreshHeaders = @{
    "Authorization" = "Bearer $($loginResponse.refreshToken)"
}

try {
    $refreshResponse = Invoke-RestMethod -Uri "http://localhost:8090/api/auth/refresh" `
        -Method Post `
        -Headers $refreshHeaders
    Write-Host "   Token refreshed successfully" -ForegroundColor Green
    Write-Host "   New Access Token: $($refreshResponse.accessToken.Substring(0, 50))..." -ForegroundColor Gray
} catch {
    Write-Host "   Token refresh failed" -ForegroundColor Red
    Write-Host $_.Exception.Message
}

# 5. Test new token
Write-Host ""
Write-Host "5. Testing new token..." -ForegroundColor Yellow
$newHeaders = @{
    "Authorization" = "Bearer $($refreshResponse.accessToken)"
}

try {
    $newMeResponse = Invoke-RestMethod -Uri "http://localhost:8090/api/auth/me" `
        -Method Get `
        -Headers $newHeaders
    Write-Host "   New token works!" -ForegroundColor Green
} catch {
    Write-Host "   New token failed" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== All tests completed ===" -ForegroundColor Cyan