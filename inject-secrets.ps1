# inject-secrets.ps1 - loads the dev secrets into the cluster.
#
# PowerShell counterpart of `make inject-secrets`. The file is not in git: platform.env.example
# next to it shows the keys.
param(
    [string]$Namespace = "order-platform",
    [string]$SecretName = "platform-secrets",
    [string]$EnvFile = ".secrets/dev/platform.env"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $EnvFile)) {
    Write-Error "Missing $EnvFile - copy .secrets/dev/platform.env.example and fill it in"
    exit 1
}

kubectl create secret generic $SecretName `
    --namespace $Namespace `
    --from-env-file=$EnvFile `
    --dry-run=client -o yaml | kubectl apply -f -

if ($LASTEXITCODE -ne 0) {
    Write-Error "Could not create the secret"
    exit 1
}

Write-Host "Secret $SecretName applied to $Namespace" -ForegroundColor Green

# The pods read the secret through an optional reference, so they start before it exists; a restart
# is what makes them pick it up.
$services = @(
    "api-gateway",
    "auth-service",
    "user-service",
    "product-service",
    "inventory-service",
    "order-service",
    "notification-service"
)
kubectl rollout restart deployment --namespace $Namespace @services

Write-Host "Waiting for the rollout..." -ForegroundColor Yellow
kubectl rollout status deployment --namespace $Namespace --timeout 10m
