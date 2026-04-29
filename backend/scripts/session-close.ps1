[CmdletBinding()]
param(
    [switch]$SkipFormat,
    [switch]$Fast,
    [switch]$WithMigration
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Resolve-Path (Join-Path $scriptDir "..")
$repoRoot = Resolve-Path (Join-Path $backendRoot "..")

$formatScript = Join-Path $scriptDir "format.ps1"
$verifyScript = Join-Path $scriptDir "verify.ps1"
$harnessScript = Join-Path $scriptDir "check-harness.ps1"
$migrationScript = Join-Path $scriptDir "verify-migration.ps1"

if (-not $SkipFormat) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $formatScript
}

& powershell -NoProfile -ExecutionPolicy Bypass -File $harnessScript

if ($Fast) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $verifyScript -Fast
} else {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $verifyScript
}

if ($WithMigration) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File $migrationScript
}

Push-Location $repoRoot
try {
    Write-Host ""
    Write-Host "Git status:"
    git -c safe.directory=C:/SSAFY/mango-project/be status --short
} finally {
    Pop-Location
}
