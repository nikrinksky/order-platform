# Скрипт инициализации схем для auth-service и user-service
# Run: .\init-db-schemas.ps1

Write-Host "Waiting for PostgreSQL..." -ForegroundColor Yellow

# Wait for PostgreSQL to be ready
$maxAttempts = 30
$attempt = 0
while ($attempt -lt $maxAttempts) {
    try {
        $result = docker exec order-platform-postgres-1 pg_isready -U platform
        if ($result -match "accepting connections") {
            Write-Host "PostgreSQL is ready" -ForegroundColor Green
            break
        }
    } catch {
        Write-Host "Attempt " + $attempt.ToString() + ": PostgreSQL not ready..."
    }
    Start-Sleep -Seconds 2
    $attempt++
}

if ($attempt -eq $maxAttempts) {
    Write-Host "Error: PostgreSQL did not start in time" -ForegroundColor Red
    exit 1
}

Write-Host "Creating auth schema and tables..." -ForegroundColor Yellow

# Create auth schema and tables for auth-service
docker exec order-platform-postgres-1 psql -U platform -d orderplatform -c "
CREATE SCHEMA IF NOT EXISTS auth;
CREATE TABLE IF NOT EXISTS auth.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS auth.roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS auth.user_roles (
    user_id BIGINT NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES auth.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX IF NOT EXISTS idx_users_username ON auth.users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON auth.users(email);
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON auth.user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON auth.user_roles(role_id);
"

Write-Host "Auth schema created successfully" -ForegroundColor Green

# Wait for user-db to be ready
Write-Host "Waiting for user-db..." -ForegroundColor Yellow
$attempt = 0
while ($attempt -lt $maxAttempts) {
    try {
        $result = docker exec order-platform-user-db-1 pg_isready -U userplatform
        if ($result -match "accepting connections") {
            Write-Host "user-db is ready" -ForegroundColor Green
            break
        }
    } catch {
        Write-Host "Attempt " + $attempt.ToString() + ": user-db not ready..."
    }
    Start-Sleep -Seconds 2
    $attempt++
}

if ($attempt -eq $maxAttempts) {
    Write-Host "Error: user-db did not start in time" -ForegroundColor Red
    exit 1
}

Write-Host "Creating user_profile schema and tables..." -ForegroundColor Yellow

# Create user_profile schema and tables for user-service
docker exec order-platform-user-db-1 psql -U userplatform -d userdb -c "
CREATE SCHEMA IF NOT EXISTS user_profile;
CREATE TABLE IF NOT EXISTS user_profile.users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS user_profile.user_roles (
    user_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ROLE_USER','ROLE_MANAGER','ROLE_ADMIN')),
    PRIMARY KEY (user_id, role)
);
CREATE INDEX IF NOT EXISTS idx_user_profile_users_username ON user_profile.users(username);
CREATE INDEX IF NOT EXISTS idx_user_profile_users_email ON user_profile.users(email);
CREATE INDEX IF NOT EXISTS idx_user_profile_user_roles_user_id ON user_profile.user_roles(user_id);
"

Write-Host "User_profile schema created successfully" -ForegroundColor Green

Write-Host "Database initialization completed!" -ForegroundColor Green
