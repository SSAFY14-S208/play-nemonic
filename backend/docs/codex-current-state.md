# Codex Current State

Last updated: 2026-05-04

## Current Focus

- Backend agent harness has been prepared for the `backend/` Spring Boot module.
- The harness now reflects the intended backend stack: Spring Boot, Java, PostgreSQL, Redis, MinIO, and Flyway.
- Team contribution and backend MR conventions are recorded for shared workflow.
- The first real backend feature API now includes anonymous user UUID issuance through `POST /api/v1/users/anonymous`.
- Existing anonymous user APIs identify the caller with the `Anonymous-User-UUID` request header instead of request body or query parameters.
- Anonymous user re-entry now includes `POST /api/v1/users/anonymous/verify` to validate the header UUID and update `last_seen_at`, `updated_at`, and `user_agent`.
- Anonymous user nickname setup/change now uses `PATCH /api/v1/users/anonymous/nickname` with the UUID in `Anonymous-User-UUID`, 1-10 code point validation, and no duplicate check.
- Anonymous user profile lookup now uses `GET /api/v1/users/anonymous/profile` with `Anonymous-User-UUID` and returns reusable profile fields without updating visit metadata.
- Anonymous user birth info now uses `POST /api/v1/users/anonymous/birth-info` for first registration and `PATCH /api/v1/users/anonymous/birth-info` for updates.
- `app_user.is_lunar` is added through Flyway V3 so fortune features can reuse birthday, birthtime, and lunar/solar selection.
- My gallery listing now uses `GET /api/v1/gallery` with `Anonymous-User-UUID` and reads existing gallery/artifact rows without MinIO calls.
- My gallery item detail now uses `GET /api/v1/gallery/{galleryId}` with `Anonymous-User-UUID` and returns one active owned gallery artifact with parsed `meta` and content URL fallback.
- My gallery deletion now uses `DELETE /api/v1/gallery/{galleryId}` with `Anonymous-User-UUID` and only updates `gallery.deleted_at`; artifact, subtype rows, community memo rows, and MinIO files are preserved.
- Files API calls (`POST /api/v1/files/presign`, `POST /api/v1/files/{fileId}/confirm`, `DELETE /api/v1/files/{fileId}`) also use `Anonymous-User-UUID`.
- Anonymous user UUID parsing and existing-user lookup are centralized in `AnonymousUserResolver`, which is reused by User, Gallery, and Files services.
- Room code generation is available through `RoomCodeGenerator`, producing 6-character uppercase human-readable codes and supporting repository-backed collision checks with `generateUnique(...)`.
- Super admin bootstrap is available through `ADMIN_BOOTSTRAP_ENABLED` and
  related `ADMIN_BOOTSTRAP_*` environment variables; it creates one
  `super_admin` row in `admin_user` only when enabled and the login ID does not
  already exist.
- Feature services now follow the `Service` interface plus `ServiceImpl` implementation structure; controllers depend on service interfaces.
- Swagger/OpenAPI docs now explicitly declare path, query, and header parameter names so UI fields do not fall back to `arg0`, `arg1`, or similar compiler-generated names.
- Swagger/OpenAPI failure responses now include representative `success: false` JSON examples for User, Gallery, Files, and Community APIs.
- The common `ApiResponse.errors` schema is documented as optional field-level validation details with a neutral example; domain-specific failure messages are documented on each API response instead.
- Upcoming backend work should continue using the feature package structure and product specs as the source of truth.

## Stable Decisions

- Root Java package is `com.nemonicworld`.
- New backend features should follow `backend/docs/backend-architecture.md`.
- Feature packages use `controller`, `service`, `repository`, `entity`, and `dto`; do not create a separate `domain` package.
- Service packages use `<Feature>Service` for the controller-facing interface and `<Feature>ServiceImpl` for the Spring `@Service` implementation.
- REST controller paths receive the common `/api/v1` prefix through `ApiPathPrefixConfig`; controller-level mappings should keep only feature paths such as `/users` or `/gallery`.
- Existing anonymous-user-scoped APIs use the common `Anonymous-User-UUID` header for caller identification; body fields are business data, query parameters are filters or pagination.
- OpenAPI controller annotations must keep request parameter names explicit (`@PathVariable("...")`, `@RequestParam(name = "...")`, `@RequestHeader(value = "...")`) and document expected failure responses with `success: false` examples.
- Commands are run from the repository root unless a script says otherwise.
- Verification is standardized through `backend/scripts/format.ps1` and `backend/scripts/verify.ps1`.
- PostgreSQL-specific Flyway migrations are verified through `backend/scripts/verify-migration.ps1`.
- Gradle, Maven, and Java tool caches are isolated inside ignored workspace directories.
- Local Docker dependencies include PostgreSQL, Redis, MinIO, and a one-shot MinIO bucket initializer.
- Repository text line endings are normalized through `.gitattributes`.
- Agent memory is stored in repo docs instead of relying only on chat history.
- Product planning is stored under `backend/docs/product-spec/` as durable AI-readable memory.
- Super admin bootstrap is environment-driven only. Do not hard-code initial
  admin passwords or password hashes in migrations, source code, or docs.

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

Latest full baseline result: `BUILD SUCCESSFUL`.

Recent Swagger/OpenAPI documentation checks passed with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1 -Fast
```

Recent room code generator work passed with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
```

`verify-migration.ps1` successfully applied the initial Flyway DDL to a real
PostgreSQL Testcontainers database after Docker Desktop was started.

## Next Suggested Steps

- Use `backend/docs/codex-prompt-templates.md` for the first feature request.
- Read the relevant product spec before designing API, DB, or event behavior.
- Add ADRs when package structure, database strategy, authentication strategy, or testing strategy changes.
- Use subagents only when explicitly requested for investigation, review, or parallel work.
- Consider enabling a weekly Codex automation for harness health checks after the team agrees on schedule.
