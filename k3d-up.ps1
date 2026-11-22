# k3d-up.ps1 - brings the platform up in a local k3d cluster.
#
# PowerShell counterpart of `make k3d-up`: Windows ships without GNU Make, and the task list asks
# for both entry points to exist.
param(
    [string]$Cluster = "order-platform",
    [string]$Namespace = "order-platform",
    [string]$Release = "order-platform",
    [string]$Chart = "helm/order-platform",
    [int]$GatewayPort = 8080,
    [int]$GrafanaPort = 3000,
    [int]$PrometheusPort = 9090,
    [int]$JaegerPort = 16686,
    [int]$MailhogPort = 8025
)

$ErrorActionPreference = "Stop"

$services = @(
    "api-gateway",
    "auth-service",
    "user-service",
    "product-service",
    "inventory-service",
    "order-service",
    "notification-service"
)

function Assert-Tool {
    param([string]$Name, [string]$Hint)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Write-Error "$Name is not installed. $Hint"
        exit 1
    }
}

Assert-Tool "k3d" "Install it with: winget install k3d.k3d"
Assert-Tool "kubectl" "Install it with: winget install Kubernetes.kubectl"
Assert-Tool "helm" "Install it with: winget install Helm.Helm"
Assert-Tool "docker" "Start Docker Desktop first."

Write-Host "Creating k3d cluster $Cluster..." -ForegroundColor Yellow
# The entry points are published through the k3d load balancer, so a service is reachable on the
# same localhost port the compose stack uses. Stop the compose stack first: two sets of containers
# cannot bind the same host port. Traefik is disabled because nothing routes through an ingress.
k3d cluster create $Cluster `
    --agents 1 `
    --port "$GatewayPort`:30080@loadbalancer" `
    --port "$GrafanaPort`:30000@loadbalancer" `
    --port "$PrometheusPort`:30090@loadbalancer" `
    --port "$JaegerPort`:30686@loadbalancer" `
    --port "$MailhogPort`:30825@loadbalancer" `
    --k3s-arg "--disable=traefik@server:0" `
    --wait

if ($LASTEXITCODE -ne 0) {
    Write-Error "k3d cluster create failed - check that the host ports are free and Docker Hub is reachable"
    exit 1
}

Write-Host "Building the images..." -ForegroundColor Yellow
docker compose build

Write-Host "Importing the images into the cluster..." -ForegroundColor Yellow
# Through containerd, not `k3d image import`: that one silently skips multi-arch images
# (redpanda, schema-registry, mailhog) and the pods then hang on a Docker Hub pull.
$serviceImages = $services | ForEach-Object { "order-platform-${_}:latest" }
$infraImages = @(
    "postgres:15-alpine",
    "mongo:7",
    "redis:7-alpine",
    "redpandadata/redpanda:v23.3.1",
    "confluentinc/cp-schema-registry:7.5.0",
    "mailhog/mailhog:latest",
    "prom/prometheus:v2.53.4",
    "grafana/grafana:11.3.0",
    "grafana/loki:2.9.8",
    "jaegertracing/all-in-one:1.62.0",
    "timberio/vector:0.49.0-debian",
    "busybox:1.36"
)
& "$PSScriptRoot\scripts\import-images.ps1" -Cluster $Cluster -Images ($serviceImages + $infraImages)

Write-Host "Creating the namespace..." -ForegroundColor Yellow
kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f -

# auth-service declares jwt.secret as ${JWT_SECRET} without a default, so the Secret has to exist
# before the pod starts - otherwise the placeholder does not resolve and the context fails on
# startup. `inject-secrets.ps1` does the same and also restarts the pods.
$envFile = ".secrets/dev/platform.env"
if (-not (Test-Path $envFile)) {
    Write-Error "Missing $envFile - copy .secrets/dev/platform.env.example and fill it in"
    exit 1
}
kubectl create secret generic platform-secrets `
    --namespace $Namespace `
    --from-env-file=$envFile `
    --dry-run=client -o yaml | kubectl apply -f -

Write-Host "Installing the chart..." -ForegroundColor Yellow
helm upgrade --install $Release $Chart --namespace $Namespace --wait --timeout 15m

Write-Host ""
Write-Host "Platform ready:" -ForegroundColor Green
Write-Host "  API Gateway / Swagger UI: http://localhost:$GatewayPort"
Write-Host "  Grafana:                  http://localhost:$GrafanaPort (admin/admin)"
Write-Host "  Prometheus:               http://localhost:$PrometheusPort"
Write-Host "  Jaeger:                   http://localhost:$JaegerPort"
Write-Host "  MailHog:                  http://localhost:$MailhogPort"
Write-Host ""
Write-Host "After editing .secrets/dev/platform.env run ./inject-secrets.ps1 to apply it" -ForegroundColor Cyan
