[CmdletBinding()]
param(
    [string]$EnvFile = ".env"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Resolve-Path (Join-Path $scriptDir "..")
$repoRoot = Resolve-Path (Join-Path $backendRoot "..")
$envPath = Join-Path $backendRoot $EnvFile
$composePath = Join-Path $backendRoot "docker-compose.local.yml"

if (-not (Test-Path $envPath)) {
    throw "Missing $envPath. Create it from backend/.env.example before starting local dependencies."
}

if (-not (Test-Path $composePath)) {
    throw "Missing $composePath"
}

Push-Location $repoRoot
try {
    docker compose --env-file $envPath -f $composePath up -d
} finally {
    Pop-Location
}
