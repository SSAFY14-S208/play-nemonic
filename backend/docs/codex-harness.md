# Codex Harness

This document describes the working harness for backend development. The goal is
to make every Codex task easy to scope, implement, and verify.

## Harness Layers

1. Root area router: `../../AGENTS.md`
2. Backend project guidance: `../AGENTS.md`
3. Verification entrypoint: `../scripts/verify.ps1`
4. Local infrastructure helpers: `../scripts/local-up.ps1`, `../scripts/local-down.ps1`
5. API request examples: `api/*.http`
6. Test support code: `../src/test/java/com/nemonicworld/support`
7. Backend architecture convention: `backend-architecture.md`
8. Codex app operating guide: `codex-app-operations.md`
9. Prompt templates: `codex-prompt-templates.md`
10. Memory strategy: `codex-memory.md`
11. Current state: `codex-current-state.md`
12. Agent workflow: `agent-workflow.md`
13. Review checklist: `agent-review-checklist.md`
14. Decisions: `decisions/`
15. Product specification: `product-spec/`
16. Skill candidate: `skills/nemonic-backend-development/SKILL.md`
17. Automation candidates: `automation-candidates.md`

## Standard Workflow

1. Read repo-root `AGENTS.md`.
2. Read `backend/AGENTS.md`.
3. Read `backend/docs/backend-architecture.md` when adding or moving backend packages.
4. Read the relevant product spec under `backend/docs/product-spec/` when implementing product behavior.
5. Inspect the affected package and tests.
6. Implement the smallest complete change.
7. Add or update tests.
8. Run the fastest relevant verification command.
9. Run the full verification wrapper before final handoff when practical.

## Verification Commands

From the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\check-harness.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1 -Fast
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\format.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1 -Build
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify-migration.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\session-close.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\session-close.ps1 -WithMigration
```

The default verification runs Gradle `check`. This includes tests, Checkstyle,
and Spotless checks through the Gradle build configuration.

`session-close.ps1` runs formatting, harness integrity checks, verification, and prints git status.
Use `session-close.ps1 -WithMigration` when Flyway migration files changed.

`verify-migration.ps1` runs Flyway migrations against a real PostgreSQL
Testcontainers database. Run it whenever `backend/src/main/resources/db/migration`
changes, because the default `test` profile uses H2 for speed.

## Local Dependency Commands

From the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\local-up.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\local-down.ps1
```

`local-up.ps1` requires `backend/.env`. Create it from `backend/.env.example`.
The local compose stack starts PostgreSQL, Redis, MinIO, and a one-shot MinIO
bucket initializer.

## Test Support

Shared test annotations live in:

```text
backend/src/test/java/com/nemonicworld/support
```

Use `@IntegrationTest` for Spring context integration tests.
Use `@HttpIntegrationTest` for random-port HTTP integration tests.

Add fixture builders to the same support package as entity objects emerge.

## API Requests

HTTP request examples live in:

```text
backend/docs/api
```

These files are intentionally simple and can be used from IntelliJ HTTP Client,
VS Code REST Client, or any compatible `.http` runner.
