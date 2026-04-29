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

if (-not (Test-Path $composePath)) {
    throw "Missing $composePath"
}

Push-Location $repoRoot
try {
    $composeArgs = @("compose")

    if (Test-Path $envPath) {
        $composeArgs += @("--env-file", $envPath)
    } else {
        Write-Warning "Env file was not found at $envPath. Running compose down without --env-file."
    }

    $composeArgs += @("-f", $composePath, "down")
    docker @composeArgs
} finally {
    Pop-Location
}
