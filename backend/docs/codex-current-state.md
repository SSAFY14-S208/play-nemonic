# Codex Current State

Last updated: 2026-05-06

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
- Backoffice admin authentication now exposes `POST /api/v1/auth/login`,
  `POST /api/v1/auth/logout`, and `POST /api/v1/auth/reissue`; admin account
  management remains under `/api/v1/admins`.
- Backoffice admin code is split by domain: `com.nemonicworld.auth` owns
  authentication and token lifecycle, while `com.nemonicworld.admin` owns admin
  account resources and super-admin bootstrap.
- Admin authentication uses JWT access tokens configured by `ADMIN_JWT_SECRET`
  and `ADMIN_JWT_ACCESS_TOKEN_EXPIRATION`; it is separate from anonymous UUID
  authentication.
- Admin authentication now also issues opaque refresh tokens stored by hash in
  Redis, rotates them through `POST /api/v1/auth/reissue`, and
  checks Redis-backed access-token revocation using JWT `jti` and
  `admin:access:revoked-after:{adminId}` markers.
- Admin logout revokes the submitted refresh token and blacklists the current
  access token; deleting a standard admin account revokes all of that account's
  refresh tokens and blocks previously issued access tokens.
- Backoffice super admins can now create standard admins with
  `POST /api/v1/admins`; the API stores BCrypt password hashes, fixes
  new accounts to the `admin` role, and rejects duplicate `login_id` values.
- Backoffice super admins can now list and inspect active admins with
  `GET /api/v1/admins` and `GET /api/v1/admins/{adminId}`.
- Backoffice super admins can now change standard admin passwords with
  `PATCH /api/v1/admins/{adminId}`; the API updates the BCrypt password hash,
  revokes the target account's refresh tokens, and blocks previously issued
  access tokens.
- Backoffice super admins can now soft-delete standard admins with
  `DELETE /api/v1/admins/{adminId}`; self-delete, super-admin target
  deletion, and missing or already deleted targets are rejected.
- Swagger/OpenAPI declares JWT bearer authentication for protected admin APIs,
  so Swagger UI can send `Authorization: Bearer <token>` through the global
  Authorize flow.
- Admin login, failed login, and logout events emit structured JSON audit logs
  to stdout using the `08-observability.md` audit schema, with no RDB audit log
  table.
- `admin_user.login_id` is made unique through Flyway V5.
- Room code generation is available through `RoomCodeGenerator`, producing 6-character uppercase human-readable codes and supporting repository-backed collision checks with `generateUnique(...)`.
- Relay room creation now uses `POST /api/v1/relay/rooms`, reuses `Anonymous-User-UUID`, requires a non-default nickname before room creation, stores the WAITING room state only in Redis under `relay:room:{roomCode}` with a 24-hour TTL, creates the host participant with `connected=false` until WebSocket CONNECT succeeds, and creates no PostgreSQL artifact/gallery rows.
- Relay room state lookup now uses `GET /api/v1/relay/rooms/{roomCode}`, reads the Redis room snapshot without mutation, sorts participants by `joinOrder`, and computes viewer join/reconnect eligibility from the requested `Anonymous-User-UUID`.
- Relay room join/reconnect now uses `POST /api/v1/relay/rooms/{roomCode}/participants`, applies Redis `WATCH`/`MULTI`/`EXEC` optimistic conditional updates for new WAITING-room participants with `connected=false`, validates 10-second reconnect eligibility without setting `connected=true`, retries short-lived write conflicts, and remains free of PostgreSQL artifact/gallery, MinIO, and WebSocket side effects.
- Relay room WebSocket lobby connections use the STOMP endpoint `/ws/relay`, CONNECT headers `roomCode` and `Anonymous-User-UUID`, topic `/topic/relay/rooms/{roomCode}`, user queue `/user/queue/relay/rooms/{roomCode}`, Redis `connected`/`disconnectedAt` updates where successful CONNECT is the only path to `connected=true` and DISCONNECT returns it to `false`, session-id-scoped duplicate-session close events, and common `global.websocket` infrastructure for single-server in-memory active session tracking.
- Relay drawing submissions now advance the Redis room state from `FACE` to `BODY` and `BODY` to `LEGS` when every assignment in the current part is `SUBMITTED` or `AUTO_SUBMITTED`; completing `LEGS` moves the room to `FINALIZING` and emits `ALL_PARTS_COMPLETED`, while final image composition, artifact/gallery persistence, and temp cleanup remain separate follow-up work.
- Relay timeout auto-submit now scans `PLAYING` Redis rooms only after `partDeadlineAt + auto-submit-grace-ms`, marks remaining current-part `PENDING` assignments as `AUTO_SUBMITTED` empty entries without MinIO upload, reuses the shared part advancement flow, and emits `PART_AUTO_SUBMITTED` plus existing transition events after successful CAS saves.
- Relay disconnect grace processing now scans candidate `PLAYING` Redis rooms after the 10-second reconnect grace, marks expired disconnected participants as `dropped` with `droppedAt`, blocks dropped UUIDs from REST rejoin and WebSocket reconnect, auto-submits only their current-part `PENDING` assignments as empty `AUTO_SUBMITTED`, leaves future part assignments pending until that part becomes current, transfers a dropped host to the lowest `joinOrder` connected non-dropped participant when available, and emits `PARTICIPANT_DROPPED`, `HOST_CHANGED`, `PART_AUTO_SUBMITTED`, and existing part transition events only after successful CAS saves.
- Relay finalization now scans `FINALIZING` Redis rooms, composes one vertical FACE/BODY/LEGS PNG per `canvasIndex`, stores final original and thumbnail objects under `relay/results/{artifactId}/`, writes matching `artifact`, `relay_drawing_artifact`, and participant gallery rows, marks the Redis room `FINISHED`, and emits `RESULT_CREATED`; presigned result URLs remain follow-up work.
- Relay result lookup now uses `GET /api/v1/relay/rooms/{roomCode}/results`, reads PostgreSQL `artifact`/`relay_drawing_artifact`/active `gallery` rows as the source of truth, returns final combined/thumbnail URLs plus canvasIndex FACE/BODY/LEGS drawer metadata parsed from `artifact.meta`, and succeeds even after Redis room state expires when DB result ownership exists.
- Relay room close now scans `FINISHED` Redis rooms after `updatedAt + close-delay` and also supports host-triggered `POST /api/v1/relay/rooms/{roomCode}/close`; both paths mark eligible rooms `CLOSED` through CAS and emit `ROOM_CLOSED` only on the successful state transition, while artifact/gallery deletion remains out of scope.
- Relay temp cleanup now scans `CLOSED` Redis rooms, collects distinct assignment `objectKey` and `hintObjectKey` values only under `relay/tmp/{roomCode}/`, hard-deletes those temporary objects from MinIO, and records cleanup completion with a separate Redis marker plus cleanup lock; it also has a fallback scheduler that lists `relay/tmp/` objects and deletes only objects older than the configured threshold (24 hours by default), while `relay/results/**` and artifact/gallery rows remain out of scope.
- Relay waiting-room host kick now uses `POST /api/v1/relay/rooms/{roomCode}/participants/kick` with `targetUserUuid` in the JSON body, removes only non-host participants while preserving remaining `joinOrder` values, records `kickedUserUuids` in the Redis room state, blocks kicked UUIDs from REST invite/join and WebSocket reconnect paths, and emits `PARTICIPANT_KICKED` plus a best-effort personal `KICKED_FROM_ROOM` queue event before closing the same-server active session.
- Relay waiting-room voluntary leave now uses `DELETE /api/v1/relay/rooms/{roomCode}/participants/me`, removes the caller without adding them to `kickedUserUuids`, preserves remaining `joinOrder` values, transfers host ownership to the lowest remaining `joinOrder` when the host leaves, marks the room `CLOSED` when the last participant leaves, emits `PARTICIPANT_LEFT` plus `HOST_CHANGED` or `ROOM_CLOSED` when applicable, and best-effort closes the leaving user's same-server active WebSocket session.
- Relay service internals are grouped under `service.room`, `service.game`, `service.assignment`, `service.submission`, `service.timeout`, `service.finalization`, `service.close`, `service.cleanup`, and `service.support`, while `RelayRoomService` and `RelayRoomServiceImpl` remain the controller-facing facade.
- Flipbook room creation now uses `POST /api/v1/flipbook/rooms`, reuses `Anonymous-User-UUID`, requires a non-default nickname before room creation, stores the WAITING room state only in Redis under `flipbook:room:{roomCode}` with a 24-hour TTL, and stores matching common invite metadata under `invite:{roomCode}` with `boothType=flipbook`.
- Flipbook room state lookup now uses `GET /api/v1/flipbook/rooms/{roomCode}`, reads the Redis room snapshot without mutation, sorts participants by `joinOrder`, and computes viewer participation, host, joinable, startable, and blocked-reason flags from the requested `Anonymous-User-UUID`.
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
- In-progress relay rooms are identified only by `roomCode`; Redis keys use `relay:room:{roomCode}` and no internal UUID room id is introduced.
- In-progress flipbook rooms also use the generated 6-character `roomCode`; Redis keys use `flipbook:room:{roomCode}`, while the shared invite index uses `invite:{roomCode}`.
- OpenAPI controller annotations must keep request parameter names explicit (`@PathVariable("...")`, `@RequestParam(name = "...")`, `@RequestHeader(value = "...")`) and document expected failure responses with `success: false` examples.
- Commands are run from the repository root unless a script says otherwise.
- Verification is standardized through `backend/scripts/format.ps1` and `backend/scripts/verify.ps1`.
- PostgreSQL-specific Flyway migrations are verified through `backend/scripts/verify-migration.ps1`.
- Gradle, Maven, and Java tool caches are isolated inside ignored workspace directories.
- Local Docker dependencies include PostgreSQL, Redis, MinIO, and a one-shot MinIO bucket initializer.
- Repository text line endings are normalized through `.gitattributes`.
- Agent memory is stored in repo docs instead of relying only on chat history.
- Product planning is stored under `backend/docs/product-spec/` as durable AI-readable memory.
- Backoffice audit logs are emitted as structured stdout JSON and are collected
  through the Fluent Bit/Kafka/OpenSearch pipeline; do not add an audit-log RDB
  table for operator action trails.
- Super admin bootstrap is environment-driven only. Do not hard-code initial
  admin passwords or password hashes in migrations, source code, or docs.
- Relay drawing durable decisions are recorded in ADR 0005 through 0011,
  covering runtime state, assignments, lobby controls, WebSocket events,
  scheduler CAS processing, file lifecycle/finalization, and result ownership.

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

Recent relay waiting-room kick work passed with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\format.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1 -Fast
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1
```

Recent flipbook lobby WebSocket work passed with:

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew compileJava spotlessCheck test --tests 'com.nemonicworld.flipbook.*' --tests 'com.nemonicworld.relay.websocket.*' --no-daemon
```

`verify-migration.ps1` successfully applied the initial Flyway DDL to a real
PostgreSQL Testcontainers database after Docker Desktop was started.

## Next Suggested Steps

- Use `backend/docs/codex-prompt-templates.md` for the first feature request.
- Read the relevant product spec before designing API, DB, or event behavior.
- Add ADRs when package structure, database strategy, authentication strategy, or testing strategy changes.
- Use subagents only when explicitly requested for investigation, review, or parallel work.
- Consider enabling a weekly Codex automation for harness health checks after the team agrees on schedule.
