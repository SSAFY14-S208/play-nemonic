# Codex Current State

Last updated: 2026-04-30

## Current Focus

- Backend agent harness has been prepared for the `backend/` Spring Boot module.
- The harness now reflects the intended backend stack: Spring Boot, Java, PostgreSQL, Redis, MinIO, and Flyway.
- Team contribution and backend MR conventions are recorded for shared workflow.
- The first real backend feature API now includes anonymous user UUID issuance through `POST /users/anonymous`.
- Anonymous user re-entry now includes `POST /users/anonymous/verify` to validate a stored UUID and update `last_seen_at`, `updated_at`, and `user_agent`.
- Anonymous user nickname setup/change now uses `PATCH /users/anonymous/nickname` with 1-10 code point validation and no duplicate check.
- Anonymous user profile lookup now uses `GET /users/anonymous/profile?userUuid=...` and returns reusable profile fields without updating visit metadata.
- Anonymous user birth info now uses `POST /users/anonymous/birth-info` for first registration and `PATCH /users/anonymous/birth-info` for updates.
- `app_user.is_lunar` is added through Flyway V3 so fortune features can reuse birthday, birthtime, and lunar/solar selection.
- My gallery listing now uses `GET /api/v1/gallery?userUuid=...` and reads existing gallery/artifact rows without MinIO calls.
- My gallery deletion now uses `DELETE /api/v1/gallery/{galleryId}?userUuid=...` and only updates `gallery.deleted_at`; artifact, subtype rows, community memo rows, and MinIO files are preserved.
- Upcoming backend work should continue using the feature package structure and product specs as the source of truth.

## Stable Decisions

- Root Java package is `com.nemonicworld`.
- New backend features should follow `backend/docs/backend-architecture.md`.
- Feature packages use `controller`, `service`, `repository`, `entity`, and `dto`; do not create a separate `domain` package.
- Commands are run from the repository root unless a script says otherwise.
- Verification is standardized through `backend/scripts/format.ps1` and `backend/scripts/verify.ps1`.
- PostgreSQL-specific Flyway migrations are verified through `backend/scripts/verify-migration.ps1`.
- Gradle, Maven, and Java tool caches are isolated inside ignored workspace directories.
- Local Docker dependencies include PostgreSQL, Redis, MinIO, and a one-shot MinIO bucket initializer.
- Repository text line endings are normalized through `.gitattributes`.
- Agent memory is stored in repo docs instead of relying only on chat history.
- Product planning is stored under `backend/docs/product-spec/` as durable AI-readable memory.

## Important Files

- `AGENTS.md`: root area-routing agent instructions
- `CONTRIBUTING.md`: root area-routing contribution guide
- `backend/AGENTS.md`: primary backend agent operating instructions
- `backend/CONTRIBUTING.md`: backend branch, commit, Jira, and verification guide
- `backend/docker-compose.local.yml`: backend local PostgreSQL, Redis, and MinIO stack
- `.gitlab/merge_request_templates/backend.md`: backend MR checklist
- `backend/docs/backend-architecture.md`: package and layer convention
- `backend/docs/codex-harness.md`: harness overview
- `backend/docs/codex-memory.md`: memory engineering strategy
- `backend/docs/codex-prompt-templates.md`: prompt templates
- `backend/docs/codex-app-operations.md`: skills, automations, and subagent usage
- `backend/docs/product-spec/`: product planning split by feature and operating domain
- `backend/scripts/verify.ps1`: canonical verification wrapper
- `backend/scripts/verify-migration.ps1`: PostgreSQL Testcontainers Flyway migration verification
- `backend/scripts/format.ps1`: canonical formatting wrapper
- `backend/scripts/check-harness.ps1`: harness integrity check
- `backend/scripts/session-close.ps1`: end-of-session validation routine
- `backend/scripts/new-decision.ps1`: ADR generator
- `backend/docs/skills/nemonic-backend-development/SKILL.md`: repo-local skill candidate
- `backend/docs/automation-candidates.md`: automation backlog

## Known Environment Notes

- `backend/.env` must be created from `backend/.env.example` before running local Docker dependencies.
- `verify-migration.ps1` requires Docker Desktop or another Docker daemon reachable by Testcontainers.
- Git may require `git config --global --add safe.directory C:/SSAFY/mango-project/be` in Codex sandbox contexts.
- Korean Markdown files are UTF-8. Some legacy PowerShell reads may render Korean text incorrectly unless UTF-8 is used.

## Latest Verification

From the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\format.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\check-harness.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify-migration.ps1
```

Latest result: `BUILD SUCCESSFUL`.

`verify-migration.ps1` successfully applied the initial Flyway DDL to a real
PostgreSQL Testcontainers database after Docker Desktop was started.

## Next Suggested Steps

- Use `backend/docs/codex-prompt-templates.md` for the first feature request.
- Read the relevant product spec before designing API, DB, or event behavior.
- Add ADRs when package structure, database strategy, authentication strategy, or testing strategy changes.
- Use subagents only when explicitly requested for investigation, review, or parallel work.
- Consider enabling a weekly Codex automation for harness health checks after the team agrees on schedule.
