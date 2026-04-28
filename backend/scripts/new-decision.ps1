[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Title
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Resolve-Path (Join-Path $scriptDir "..")
$decisionDir = Join-Path $backendRoot "docs\decisions"

New-Item -ItemType Directory -Force -Path $decisionDir | Out-Null

$existingNumbers = Get-ChildItem -Path $decisionDir -Filter "*.md" |
    Where-Object { $_.BaseName -match "^\d{4}-" } |
    ForEach-Object { [int]$_.BaseName.Substring(0, 4) }

$nextNumber = 1
if ($existingNumbers) {
    $nextNumber = ($existingNumbers | Measure-Object -Maximum).Maximum + 1
}

$slug = $Title.ToLowerInvariant()
$slug = [regex]::Replace($slug, "[^a-z0-9]+", "-").Trim("-")
if (-not $slug) {
    $slug = "decision"
}

$fileName = "{0:D4}-{1}.md" -f $nextNumber, $slug
$filePath = Join-Path $decisionDir $fileName

if (Test-Path $filePath) {
    throw "Decision file already exists: $filePath"
}

$date = Get-Date -Format "yyyy-MM-dd"
$content = @"
# {0:D4} $Title

Date: $date

## Status

Proposed

## Context

What problem are we solving?

## Decision

What did we decide?

## Consequences

- Positive:
- Negative:
- Follow-up:
"@ -f $nextNumber

[System.IO.File]::WriteAllText($filePath, $content, [System.Text.UTF8Encoding]::new($false))
Write-Host "Created $filePath"
