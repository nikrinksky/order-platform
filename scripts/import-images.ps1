# import-images.ps1 - loads the platform and infrastructure images into a k3d cluster.
#
# PowerShell counterpart of scripts/import-images.sh; see its header for why containerd is used
# directly instead of `k3d image import`.
param(
    [Parameter(Mandatory = $true)]
    [string]$Cluster,
    [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)]
    [string[]]$Images
)

$ErrorActionPreference = "Stop"

# ConvertFrom-Json receives the whole document as one string: piping the command straight into it
# feeds the cmdlet line by line and only the first fragment is parsed.
$nodeList = (k3d node list -o json | Out-String) | ConvertFrom-Json
# serverlb is an nginx container, not a k3s node: it has no containerd and no ctr
# k3d prefixes the container names with "k3d-", so the cluster name alone does not match
$nodes = $nodeList |
    Where-Object { $_.name -like "k3d-$Cluster-*" -and $_.role -ne "loadbalancer" } |
    Select-Object -ExpandProperty name

if (-not $nodes) {
    Write-Error "No nodes found for cluster $Cluster"
    exit 1
}

$tmpFile = Join-Path $env:TEMP "import-image.tar"

foreach ($image in $Images) {
    Write-Host "Importing $image..." -ForegroundColor Yellow
    docker save -o $tmpFile $image

    foreach ($node in $nodes) {
        docker cp $tmpFile "${node}:/tmp/import-image.tar"
        # docker save writes the manifest list, but the tarball only carries the blobs of the
        # current platform. Without --platform containerd tries to resolve every listed platform
        # and fails with "content digest ... not found".
        docker exec $node ctr -n k8s.io images import --platform linux/amd64 /tmp/import-image.tar | Out-Null
        docker exec $node rm -f /tmp/import-image.tar
    }

    Remove-Item $tmpFile -Force
}

Write-Host "Imported $($Images.Count) image(s) into cluster $Cluster" -ForegroundColor Green
