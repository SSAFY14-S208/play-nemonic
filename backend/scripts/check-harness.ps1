[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Resolve-Path (Join-Path $scriptDir "..")
$repoRoot = Resolve-Path (Join-Path $backendRoot "..")

$requiredPaths = @(
    "AGENTS.md",
    "CONTRIBUTING.md",
    ".gitlab/merge_request_templates/backend.md",
    "backend/docs/codex-harness.md",
    "backend/docs/codex-memory.md",
    "backend/docs/codex-current-state.md",
    "backend/docs/backend-architecture.md",
    "backend/docs/codex-prompt-templates.md",
    "backend/docs/codex-app-operations.md",
    "backend/docs/agent-workflow.md",
    "backend/docs/agent-review-checklist.md",
    "backend/docs/session-handoff-template.md",
    "backend/docs/automation-candidates.md",
    "backend/docs/product-spec/README.md",
    "backend/docs/product-spec/01-community-canvas.md",
    "backend/docs/product-spec/02-relay-drawing.md",
    "backend/docs/product-spec/03-fortune.md",
    "backend/docs/product-spec/04-flipbook.md",
    "backend/docs/product-spec/05-infinite-canvas.md",
    "backend/docs/product-spec/06-common-and-inquiry.md",
    "backend/docs/product-spec/07-backoffice.md",
    "backend/docs/product-spec/08-observability.md",
    "backend/docs/skills/nemonic-backend-development/SKILL.md",
    "backend/docs/decisions/README.md",
    "backend/docs/decisions/TEMPLATE.md",
    "backend/scripts/format.ps1",
    "backend/scripts/verify.ps1",
    "backend/scripts/check-harness.ps1",
    "backend/scripts/session-close.ps1",
    "backend/scripts/verify-migration.ps1",
    "backend/scripts/new-decision.ps1",
    "backend/docs/api/health.http",
    "backend/src/main/resources/db/migration",
    "backend/src/test/java/com/nemonicworld/support/IntegrationTest.java",
    "backend/src/test/java/com/nemonicworld/support/HttpIntegrationTest.java",
    "backend/src/migrationTest/java/com/nemonicworld/support/FlywayPostgresMigrationTest.java"
)

$requiredIgnoreEntries = @(
    ".gradle-user-home",
    ".m2-repository",
    ".tool-home"
)

$requiredEnvExampleKeys = @(
    "DB_URL",
    "DB_NAME",
    "DB_USERNAME",
    "DB_PASSWORD",
    "SERVER_PORT",
    "REDIS_HOST",
    "REDIS_PORT",
    "MINIO_ENDPOINT",
    "MINIO_PUBLIC_URL",
    "MINIO_ACCESS_KEY",
    "MINIO_SECRET_KEY",
    "MINIO_BUCKET"
)

$requiredComposeSnippets = @(
    "postgres:",
    "redis:",
    "minio:",
    "minio-init:",
    "minio_data:"
)

$requiredGradleSnippets = @(
    "io.minio:minio",
    "org.testcontainers:junit-jupiter",
    "org.testcontainers:postgresql",
    "migrationTest"
)

$missing = @()

foreach ($path in $requiredPaths) {
    $fullPath = Join-Path $repoRoot $path
    if (-not (Test-Path $fullPath)) {
        $missing += $path
    }
}

if ($missing.Count -gt 0) {
    Write-Error "Missing harness paths:`n$($missing -join "`n")"
}

$gitignore = Get-Content -Raw -Encoding UTF8 (Join-Path $backendRoot ".gitignore")
$dockerignore = Get-Content -Raw -Encoding UTF8 (Join-Path $backendRoot ".dockerignore")
$envExample = Get-Content -Raw -Encoding UTF8 (Join-Path $backendRoot ".env.example")
$compose = Get-Content -Raw -Encoding UTF8 (Join-Path $repoRoot "docker-compose.local.yml")
$buildGradle = Get-Content -Raw -Encoding UTF8 (Join-Path $backendRoot "build.gradle")

foreach ($entry in $requiredIgnoreEntries) {
    if ($gitignore -notmatch [regex]::Escape($entry)) {
        Write-Error "backend/.gitignore is missing $entry"
    }

    if ($dockerignore -notmatch [regex]::Escape($entry)) {
        Write-Error "backend/.dockerignore is missing $entry"
    }
}

foreach ($key in $requiredEnvExampleKeys) {
    if ($envExample -notmatch "(?m)^$([regex]::Escape($key))=") {
        Write-Error "backend/.env.example is missing $key"
    }
}

foreach ($snippet in $requiredComposeSnippets) {
    if ($compose -notmatch [regex]::Escape($snippet)) {
        Write-Error "docker-compose.local.yml is missing $snippet"
    }
}

foreach ($snippet in $requiredGradleSnippets) {
    if ($buildGradle -notmatch [regex]::Escape($snippet)) {
        Write-Error "backend/build.gradle is missing $snippet"
    }
}

Write-Host "Harness check passed."
