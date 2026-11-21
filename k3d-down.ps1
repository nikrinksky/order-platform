# k3d-down.ps1 - removes the release and the cluster.
param(
    [string]$Cluster = "order-platform",
    [string]$Namespace = "order-platform",
    [string]$Release = "order-platform"
)

$ErrorActionPreference = "Continue"

Write-Host "Removing the release and the cluster..." -ForegroundColor Yellow
helm uninstall $Release --namespace $Namespace 2>$null
k3d cluster delete $Cluster

Write-Host "Cluster removed" -ForegroundColor Green
