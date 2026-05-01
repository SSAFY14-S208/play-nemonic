[CmdletBinding()]
param(
    [switch]$Fast,
    [switch]$Build,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Resolve-Path (Join-Path $scriptDir "..")
$gradleFile = "gradlew"

if ($env:OS -eq "Windows_NT") {
    $gradleFile = "gradlew.bat"
}

$gradlePath = Join-Path $backendRoot $gradleFile

if (-not (Test-Path $gradlePath)) {
    throw "Gradle wrapper was not found at $gradlePath"
}

$env:GRADLE_USER_HOME = Join-Path $backendRoot ".gradle-user-home"
$mavenRepo = Join-Path $backendRoot ".m2-repository"
$toolHome = Join-Path $backendRoot ".tool-home"

New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME | Out-Null
New-Item -ItemType Directory -Force -Path $mavenRepo | Out-Null
New-Item -ItemType Directory -Force -Path $toolHome | Out-Null

Push-Location $backendRoot
try {
    $tasks = @()

    if ($Clean) {
        $tasks += "clean"
    }

    if ($Fast) {
        $tasks += "test"
    } else {
        $tasks += "check"
    }

    if ($Build) {
        $tasks += "build"
    }

    Write-Host "Using GRADLE_USER_HOME: $env:GRADLE_USER_HOME"
    Write-Host "Using Maven local repository: $mavenRepo"
    Write-Host "Using tool home: $toolHome"
    Write-Host "Running Gradle tasks: $($tasks -join ' ')"
    & $gradlePath "--no-daemon" "-Dmaven.repo.local=$mavenRepo" "-Duser.home=$toolHome" @tasks

    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
