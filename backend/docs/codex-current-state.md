# Codex Current State

Last updated: 2026-05-10

## Current Focus

- Backend agent harness has been prepared for the `backend/` Spring Boot module.
- The harness now reflects the intended backend stack: Spring Boot, Java, PostgreSQL, Redis, MinIO, and Flyway.
- Team contribution and backend MR conventions are recorded for shared workflow.
- The first real backend feature API now includes anonymous user UUID issuance through `POST /api/v1/users/anonymous`.
- Backend runtime now sets the JVM default timezone from `nemonic.time-zone`
  (`APP_TIME_ZONE`, default `Asia/Seoul`) during application startup so
  `LocalDateTime.now()` based DB writes and API responses follow the Korean
  service timezone consistently.
- Backend Gradle tests now start the test JVM with
  `user.timezone=Asia/Seoul` so CI date/time assertions stay aligned with the
  Korean service timezone even when the Jenkins host uses UTC.
- Existing anonymous user APIs identify the caller with the `Anonymous-User-UUID` request header instead of request body or query parameters.
- Anonymous user re-entry now includes `POST /api/v1/users/anonymous/verify` to validate the header UUID and update `last_seen_at`, `updated_at`, and `user_agent`.
- Anonymous user nickname setup/change now uses `PATCH /api/v1/users/anonymous/nickname` with the UUID in `Anonymous-User-UUID`, 1-10 code point validation, and no duplicate check.
- Anonymous user profile lookup now uses `GET /api/v1/users/anonymous/profile` with `Anonymous-User-UUID` and returns reusable profile fields without updating visit metadata.
- Anonymous user birth info now uses `POST /api/v1/users/anonymous/birth-info` for first registration and `PATCH /api/v1/users/anonymous/birth-info` for updates.
- `app_user.is_lunar` is added through Flyway V3 so fortune features can reuse birthday, birthtime, and lunar/solar selection.
- My gallery listing now uses `GET /api/v1/gallery` with `Anonymous-User-UUID` and reads existing gallery/artifact rows without MinIO calls.
- My gallery item detail now uses `GET /api/v1/gallery/{galleryId}` with `Anonymous-User-UUID` and returns one active owned gallery artifact with parsed `meta` and content URL fallback.
- My gallery deletion now uses `DELETE /api/v1/gallery/{galleryId}` with `Anonymous-User-UUID` and only updates `gallery.deleted_at`; artifact, subtype rows, community memo rows, and MinIO files are preserved.
- Phone drawings can now be saved into the user's gallery through
  `POST /api/v1/gallery/drawings`; the API accepts confirmed `PHONE`
  `file_upload` rows, stores a new `artifact(kind=phone)`, matching
  `phone_artifact`, and `gallery` row, and returns public image URLs while
  keeping DB storage object-key based. PHONE presigned uploads use
  `phone/results/{fileId}/{fileName}` object keys to align with gallery result
  storage paths.
- Gallery list/detail and relay result APIs now convert stored MinIO object keys into browser-renderable public URLs through `global.storage.minio.MinioPublicUrlResolver`, while preserving already absolute URLs as-is and keeping the database storage model object-key based.
- Files API calls (`POST /api/v1/files/presign`, `POST /api/v1/files/{fileId}/confirm`, `DELETE /api/v1/files/{fileId}`) also use `Anonymous-User-UUID`.
- Files presigned PUT/GET URLs are signed with the public MinIO origin and then re-prefixed with the configured `MINIO_PUBLIC_URL` path such as `/minio`, because the MinIO Java SDK does not allow path segments inside the client endpoint.
- Files private GET view URLs use a separate `MINIO_VIEW_URL_EXPIRATION_MINUTES` setting with a 24-hour default, while upload PUT presigned URLs keep the shorter `MINIO_PRESIGN_EXPIRATION_MINUTES` setting.
- Anonymous CS inquiry creation now uses `POST /api/v1/inquiries` with
  `Anonymous-User-UUID`, stores into the existing `cs_inquiry` table with
  initial status `new`, and preserves optional attachments and metadata as JSON
  text without adding a new migration.
- Backoffice admins can now list customer inquiries through
  `GET /api/v1/admin/inquiries`; the API requires an admin JWT, supports
  `status`, `type`, `keyword`, `userUuid`, `page`, and `size` filters, returns
  the local pagination DTO shape, and omits detail-only fields such as
  `content`, `attachments`, `meta`, and `responseNote`.
- Backoffice admins can now inspect one customer inquiry through
  `GET /api/v1/admin/inquiries/{inquiryId}`; the API requires an admin JWT,
  returns detail-only fields including parsed `attachments`, parsed `meta`,
  `assignedTo`, `responseNote`, and `respondedAt`, and treats missing or
  invalid inquiry IDs with Korean error messages.
- Backoffice admins can now reply to customer inquiries by email through
  `POST /api/v1/admin/inquiries/{inquiryId}/reply`; the API sends SMTP mail
  before marking the inquiry `resolved`, then stores `assignedTo`,
  `responseNote`, `respondedAt`, and `updatedAt`. SMTP settings are
  environment-driven through `MAIL_*` variables.
- Backoffice admins can now change a customer inquiry status through
  `PATCH /api/v1/admin/inquiries/{inquiryId}/status`; the API accepts
  `new`, `in_progress`, `resolved`, and `closed`, updates only `status` and
  `updatedAt`, and leaves reply fields such as `assignedTo`, `responseNote`,
  and `respondedAt` untouched.
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
- Backoffice admins can now create GMS prompt templates through
  `POST /api/v1/backoffice/gms/prompts`; the API writes to the existing
  `gms_prompt_template` columns (`prompt_name`, `template_text`,
  `feature_type`, `created_by`, timestamps) without changing the DB schema, and
  rejects duplicate prompt names.
- Backoffice admins can now soft-delete GMS prompt templates through
  `DELETE /api/v1/backoffice/gms/prompts/{promptId}`; the API updates
  `gms_prompt_template.deleted_at` and `updated_at` without changing the DB
  schema, and treats missing or already deleted prompts as not found.
- Backoffice admins can now update active GMS prompt templates through
  `PATCH /api/v1/backoffice/gms/prompts/{promptId}`; the API accepts optional
  `name`, `content`, and `featureType` fields, updates existing
  `gms_prompt_template` columns and `updated_at` without changing the DB
  schema, and rejects empty update bodies, duplicate prompt names, missing
  prompts, and already deleted prompts.
- Backoffice admins can now inspect one active GMS prompt template through
  `GET /api/v1/backoffice/gms/prompts/{promptId}`; the API reuses
  `GmsPromptResponse`, reads only `deleted_at IS NULL` rows from the existing
  `gms_prompt_template` table, and treats missing or already deleted prompts as
  not found.
- Backoffice admins can now list active GMS prompt templates through
  `GET /api/v1/backoffice/gms/prompts`; the API supports `keyword`,
  `featureType`, `page`, and `size`, returns the local pagination DTO shape
  (`items`, `page`, `size`, `totalElements`, `hasNext`), and reads only
  `deleted_at IS NULL` rows from the existing `gms_prompt_template` table.
- Backoffice admins can now list system parameters through
  `GET /api/v1/backoffice/system-parameters`; the API requires an admin JWT,
  reads existing `backoffice_setting` rows sorted by `setting_key ASC`,
  supports optional `keyword` search on `setting_key`, parses
  `setting_value` JSON text into the response `value`, and Flyway V8 seeds
  initial backoffice setting rows without changing the schema.
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
- Relay room join/reconnect now uses `POST /api/v1/relay/rooms/{roomCode}/participants`, applies Redis `WATCH`/`MULTI`/`EXEC` optimistic conditional updates for new WAITING-room participants with `connected=false`, keeps WAITING-room REST re-entry open without a reconnect grace cutoff unless the UUID was kicked, validates the 10-second reconnect grace only for PLAYING rooms without setting `connected=true`, retries short-lived write conflicts, and remains free of PostgreSQL artifact/gallery, MinIO, and WebSocket side effects.
- Relay room WebSocket lobby connections use the STOMP endpoint `/ws/relay`, CONNECT headers `roomCode` and `Anonymous-User-UUID`, topic `/topic/relay/rooms/{roomCode}`, user queue `/user/queue/relay/rooms/{roomCode}`, Redis `connected`/`disconnectedAt` updates where successful CONNECT is the only path to `connected=true` and DISCONNECT returns it to `false`, applies the reconnect grace cutoff only in PLAYING rooms, keeps WAITING-room reconnection available without a time cutoff, session-id-scoped duplicate-session close events, and common `global.websocket` infrastructure for single-server in-memory active session tracking.
- Relay drawing submissions now advance the Redis room state from `FACE` to `BODY` and `BODY` to `LEGS` when every assignment in the current part is `SUBMITTED` or `AUTO_SUBMITTED`; completing `LEGS` moves the room to `FINALIZING` and emits `ALL_PARTS_COMPLETED`, while final image composition, artifact/gallery persistence, and temp cleanup remain separate follow-up work.
- Relay timeout auto-submit now scans `PLAYING` Redis rooms only after `partDeadlineAt + auto-submit-grace-ms`, marks remaining current-part `PENDING` assignments as `AUTO_SUBMITTED` empty entries without MinIO upload, reuses the shared part advancement flow, and emits `PART_AUTO_SUBMITTED` plus existing transition events after successful CAS saves.
- Relay drawing submissions now acquire assignment-scoped Redis submit-in-progress locks before file upload; the timeout scheduler skips locked pending assignments until the lock TTL expires, preventing deadline-time user submissions from racing against `AUTO_SUBMITTED` fallback processing.
- Relay disconnect grace processing now scans candidate `PLAYING` Redis rooms after the configured reconnect grace, takes the room mutation lock before mutating room state, marks expired disconnected participants as `dropped` with `droppedAt`, blocks dropped UUIDs from REST rejoin and WebSocket reconnect, auto-submits only their current-part unlocked `PENDING` assignments as empty `AUTO_SUBMITTED`, leaves future part assignments pending until that part becomes current, transfers a dropped host to the lowest `joinOrder` connected non-dropped participant when available, and emits `PARTICIPANT_DROPPED`, `HOST_CHANGED`, `PART_AUTO_SUBMITTED`, and existing part transition events only after successful CAS saves.
- Relay finalization now scans `FINALIZING` Redis rooms after a short ready delay, composes one vertical FACE/BODY/LEGS PNG per `canvasIndex`, stores final original and thumbnail objects under `relay/results/{artifactId}/`, writes matching `artifact`, `relay_drawing_artifact`, and participant gallery rows, marks the Redis room `FINISHED`, emits `RESULT_CREATED`, and uses token-scoped Redis finalization locks so an expired worker cannot release another worker's lock; presigned result URLs remain follow-up work.
- Relay result lookup now uses `GET /api/v1/relay/rooms/{roomCode}/results`, reads PostgreSQL `artifact`/`relay_drawing_artifact`/active `gallery` rows as the source of truth, returns final combined/thumbnail URLs plus canvasIndex FACE/BODY/LEGS drawer metadata parsed from `artifact.meta`, and succeeds even after Redis room state expires when DB result ownership exists.
- Relay room close now scans `FINISHED` Redis rooms after `updatedAt + close-delay` and also supports host-triggered `POST /api/v1/relay/rooms/{roomCode}/close`; both paths mark eligible rooms `CLOSED` through CAS, sync invite metadata, emit `ROOM_CLOSED`, and best-effort close same-server active WebSocket sessions only on the successful state transition, while artifact/gallery deletion remains out of scope.
- Relay temp cleanup now scans `CLOSED` Redis rooms, collects distinct assignment `objectKey` and `hintObjectKey` values only under `relay/tmp/{roomCode}/`, hard-deletes those temporary objects from MinIO, and records cleanup completion with a separate Redis marker plus cleanup lock; it also has a fallback scheduler that lists `relay/tmp/` objects and deletes only objects older than the configured threshold (24 hours by default), while `relay/results/**` and artifact/gallery rows remain out of scope.
- Relay waiting-room host kick now uses `POST /api/v1/relay/rooms/{roomCode}/participants/kick` with `targetUserUuid` in the JSON body, removes only non-host participants while preserving remaining `joinOrder` values, records `kickedUserUuids` in the Redis room state, blocks kicked UUIDs from REST invite/join and WebSocket reconnect paths, and emits `PARTICIPANT_KICKED` plus a best-effort personal `KICKED_FROM_ROOM` queue event before closing the same-server active session.
- Relay waiting-room voluntary leave now uses `DELETE /api/v1/relay/rooms/{roomCode}/participants/me`, removes the caller without adding them to `kickedUserUuids`, preserves remaining `joinOrder` values, transfers host ownership to the lowest remaining `joinOrder` when the host leaves, marks the room `CLOSED` when the last participant leaves, emits `PARTICIPANT_LEFT` plus `HOST_CHANGED` or `ROOM_CLOSED` when applicable, and best-effort closes the leaving user's same-server active WebSocket session.
- Relay service internals are grouped under `service.room`, `service.game`, `service.assignment`, `service.submission`, `service.timeout`, `service.finalization`, `service.close`, `service.cleanup`, and `service.support`, while `RelayRoomService` and `RelayRoomServiceImpl` remain the controller-facing facade.
- Flipbook room creation now uses `POST /api/v1/flipbook/rooms`, reuses `Anonymous-User-UUID`, requires a non-default nickname before room creation, stores the WAITING room state only in Redis under `flipbook:room:{roomCode}` with a 24-hour TTL, and stores matching common invite metadata under `invite:{roomCode}` with `boothType=flipbook`.
- Flipbook room state lookup now uses `GET /api/v1/flipbook/rooms/{roomCode}`, reads the Redis room snapshot without mutation, sorts participants by `joinOrder`, and computes viewer participation, host, joinable, startable, and blocked-reason flags from the requested `Anonymous-User-UUID`.
- Flipbook waiting-room host kick now uses `POST /api/v1/flipbook/rooms/{roomCode}/kick` with `targetUserUuid` in the JSON body, removes only non-host participants while preserving remaining `joinOrder` values, records `kickedUserUuids` in the Redis room state, blocks kicked UUIDs from invite re-entry and WebSocket reconnect paths, and emits `PARTICIPANT_KICKED` plus a best-effort personal `KICKED_FROM_ROOM` queue event before closing the same-server active session.
- Flipbook game start now uses `POST /api/v1/flipbook/rooms/{roomCode}/start`, requires the caller to be the host of a WAITING room, requires at least two connected WebSocket participants, calculates the default total rounds from the minimum 8-frame policy, stores `currentRound`, `totalRounds`, round deadline, `gameStartedAt`, and generated frame assignments in Redis, syncs invite TTL metadata, and emits `GAME_STARTED`.
- Flipbook current assignment lookup now uses `GET /api/v1/flipbook/rooms/{roomCode}/assignments/me`, requires the caller to be a non-dropped participant in a PLAYING room, returns the current round assignment, remaining seconds, and previous-frame hint metadata when a submitted/auto-submitted previous frame exists.
- Flipbook PLAYING-room re-entry now applies a 10-second reconnect grace period to both common invite re-entry and WebSocket CONNECT; the frontend should call invite and immediately open WebSocket, and either path returns the reconnect-expired 409 once `disconnectedAt + 10s` has passed.
- Super admin bootstrap is available through `ADMIN_BOOTSTRAP_ENABLED` and
  related `ADMIN_BOOTSTRAP_*` environment variables; it creates one
  `super_admin` row in `admin_user` only when enabled and the login ID does not
  already exist.
- Feature services now follow the `Service` interface plus `ServiceImpl` implementation structure; controllers depend on service interfaces.
- Swagger/OpenAPI docs now explicitly declare path, query, and header parameter names so UI fields do not fall back to `arg0`, `arg1`, or similar compiler-generated names.
- Swagger/OpenAPI failure responses now include representative `success: false` JSON examples for User, Gallery, and Files APIs.
- The common `ApiResponse.errors` schema is documented as optional field-level validation details with a neutral example; domain-specific failure messages are documented on each API response instead.
- Community canvas product planning now defines a first backend phase focused on memo CRUD and 50-item FIFO, using the existing Files API with `purpose=COMMUNITY` for direct memo image uploads, a single create flow split by `sourceType` (`DIRECT` or `GALLERY`), object-key storage with public URL responses, front-end-friendly `ownedByMe` list responses, decoration JSON pass-through, and no WebSocket requirement for the initial CRUD/FIFO phase.
- The sample `GET /api/v1/community/{communityId}` API, its sample service/DTO, OpenAPI test, and `.http` request were removed; actual community canvas APIs should be implemented in follow-up MRs from `backend/docs/product-spec/01-community-canvas.md`.
- `CommunityMemoImageUrlResolver` converts community memo MinIO object keys into public renderable URLs using configured `publicUrl` and `bucket`, passes through absolute URLs, and performs no MinIO existence checks or presigned URL issuance.
- Community memo listing now uses `GET /api/v1/community/memos` to return visible `community_memo` rows (`deleted_at IS NULL`, `is_hidden = false`) ordered by `z_index ASC, attached_at ASC`; optional `Anonymous-User-UUID` is parsed only for `ownedByMe` and does not require app user lookup or visit metadata updates.
- Community memo detail now uses `GET /api/v1/community/memos/{memoId}` for visible memos only, returns list fields plus decoration/artifact/moderation metadata, parses optional `Anonymous-User-UUID` only for `ownedByMe`, and falls back to `{}` for blank or invalid decoration JSON.
- Community memo creation now uses `POST /api/v1/community/memos` with required `Anonymous-User-UUID`, supports `sourceType=DIRECT` and `sourceType=GALLERY`, requires distinct confirmed `COMMUNITY` `originalFileId` and `thumbnailFileId`, links GALLERY posts to an owned active gallery artifact only for source attribution, runs pre-publication moderation before insert, stores the final original object key in `community_memo.body_image_url` and thumbnail object key in `community_memo.thumbnail_image_url`, and applies 50-visible-memo FIFO soft deletion with `deleted_reason=expired`.
- Community memo layout updates now use `PATCH /api/v1/community/memos/{memoId}` with required `Anonymous-User-UUID`; only the owner of a visible memo can update `position_x`, `position_y`, `z_index`, `rotation_deg`, and `updated_at`, while image keys, artifact linkage, decoration, moderation fields, `attached_at`, and FIFO state remain untouched.
- Community memo deletion now uses `DELETE /api/v1/community/memos/{memoId}` with required `Anonymous-User-UUID`; only the owner of a visible memo can soft delete it with `deleted_reason=user_delete`, while MinIO files, file_upload rows, artifact/gallery links, moderation data, and FIFO restoration state remain untouched.
- Community memo reporting now uses `POST /api/v1/community/memos/{memoId}/reports` with required `Anonymous-User-UUID`; visible non-owned memos can be reported once per user with Korean report categories plus optional nullable `reasonDetail`, and `report_count >= 5` automatically hides the memo with `hidden_reason=report_threshold` without invoking FIFO or moderation.
- Community canvas planning now treats each posted memo as a final rendered image snapshot: frontend editing can start from a blank canvas or gallery source, then uploads both original and thumbnail `COMMUNITY` files, with `thumbnail_image_url` planned as a required schema addition; `clientText` is included as OCR moderation helper input, the original gallery artifact remains source attribution only, rendering/moderation use the posted snapshot, the default moderation policy is pre-publication FastAPI blocking with a hidden `pending` fallback only if synchronous latency becomes unacceptable, first-pass updates are layout-only, and external sharing remains a follow-up scope.
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

Recent MinIO file/view URL and public artifact URL work passed with:

```bash
cd backend
GRADLE_USER_HOME=.gradle-user-home ./gradlew spotlessApply --no-daemon
GRADLE_USER_HOME=.gradle-user-home ./gradlew test --tests 'com.nemonicworld.files.*' --tests 'com.nemonicworld.gallery.controller.GalleryControllerIntegrationTest' --tests 'com.nemonicworld.relay.controller.RelayRoomResultsControllerIntegrationTest' --tests 'com.nemonicworld.relay.service.RelayRoomResultQueryUseCaseTest' --tests 'com.nemonicworld.relay.service.RelayRoomServiceImplTest' --no-daemon
```

Recent GMS prompt creation API work passed with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\format.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\backend\scripts\verify.ps1 -Fast
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

Recent flipbook waiting-room kick work passed with:

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew spotlessCheck --no-daemon
GRADLE_USER_HOME=.gradle-user-home ./gradlew compileJava test --tests 'com.nemonicworld.flipbook.*' --tests 'com.nemonicworld.invite.service.FlipbookInviteJoinHandlerTest' --tests 'com.nemonicworld.global.websocket.session.WebSocketSessionRegistryTest' --no-daemon
```

Recent relay invite TTL synchronization work passed with:

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew spotlessCheck test --tests 'com.nemonicworld.relay.service.*' --tests 'com.nemonicworld.invite.service.*' --tests 'com.nemonicworld.relay.controller.*' --no-daemon
```

Recent flipbook game start work passed with:

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew spotlessCheck test --tests 'com.nemonicworld.flipbook.*' --tests 'com.nemonicworld.invite.service.FlipbookInviteJoinHandlerTest' --no-daemon
```

Recent file private view URL work passed with:

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew spotlessCheck test --tests 'com.nemonicworld.files.*' --no-daemon
GRADLE_USER_HOME=.gradle-user-home ./gradlew test --tests 'com.nemonicworld.relay.controller.RelayRoomAssignmentControllerIntegrationTest' --no-daemon
```

Recent flipbook reconnect grace work added PLAYING-room disconnect scanning:

- `flipbook:room:{roomCode}` participants now keep `dropped`/`droppedAt` fields like relay.
- The flipbook disconnect scheduler scans PLAYING rooms, marks participants dropped after the 10-second reconnect grace, blocks dropped users from invite/WebSocket reconnect, and transfers a dropped host to the connected non-dropped participant with the lowest `joinOrder`.
- `PARTICIPANT_DROPPED` and `HOST_CHANGED` WebSocket events are emitted after successful Redis CAS updates.

Recent flipbook current assignment lookup work passed with:

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew test --tests 'com.nemonicworld.flipbook.*' --tests 'com.nemonicworld.invite.service.FlipbookInviteJoinHandlerTest' --no-daemon
```

Recent artifact image URL lookup work added `GET /api/v1/artifacts/{artifactId}/image-urls`.

- Keep `GET /api/v1/files/{fileId}/view-url` for `file_upload.id` based private upload lookup only.
- The artifact image URL API reads active `gallery` ownership, `artifact.thumbnail_url`, and subtype image columns, then converts object keys with `global.storage.minio.MinioPublicUrlResolver`.
- Flipbook responses return multiple `contents` entries, including `gif` and `first_image` when both object keys exist.
- MinIO shared infrastructure now lives under `com.nemonicworld.global.storage.minio`; the `files` package remains scoped to `file_upload` based upload/presign/confirm/view/delete behavior.

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew spotlessCheck test --tests 'com.nemonicworld.artifact.*' --no-daemon
```

Recent flipbook result lookup work added `GET /api/v1/flipbook/rooms/{roomCode}/result`.

- Existing artifact/gallery rows are returned first for idempotent result lookup.
- If Redis room state is `FINISHED` and no DB result exists yet, submitted non-empty frames are grouped by `flipbookIndex`, converted into GIF files under `flipbook/results/{artifactId}/result.gif`, and stored as `artifact` + `flipbook_artifact` + gallery rows for non-dropped participants.
- The response mirrors relay result shape with `ready`, `resultCount`, per-result `galleryId`/`artifactId`, `thumbnailUrl`, `gifUrl`, `firstImageUrl`, and ordered frame metadata.

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew spotlessCheck test --tests 'com.nemonicworld.flipbook.*' --no-daemon
```

Recent relay submission concurrency work added a room-scoped Redis mutation lock.

- Assignment submit locks still protect a single user/canvas/part submission from timeout auto-submit.
- Room mutation locks now serialize `relay:room:{roomCode}` JSON updates between submission API requests and timeout auto-submit.
- The mutation lock key uses `relay:room-mutation-lock:{roomCode}` so it is not picked up by existing `relay:room:*` room scans.
- Submission uploads still happen before the room mutation lock; only latest room state read, validation, mutation, and save run inside the lock.

Recent fortune result re-query work added `GET /api/v1/fortune/today`.

- The API reuses `Anonymous-User-UUID`, resolves the KST current date, reads the caller's stored `fortune_artifact.description`, and returns the same result fields as fortune creation without calling GMS or card storage.
- Fortune create/re-query responses now use `FortuneResponse`, grouping rendered content under `fortune`, saved request data under `saju`, and card render metadata under `design`.
- Birth-time unknown flows are supported: `hourPillar` is optional/nullable in request and response, while `cardTheme`/`bgColor`/`accentColor`/`iconKey` may also be null until card asset metadata is ready.
- Missing same-day fortune rows return 404 with `오늘 생성된 운세를 찾을 수 없습니다.`, while malformed stored result JSON returns the existing common error envelope as a bad request.

```bash
./gradlew test --tests 'com.nemonicworld.fortune.controller.FortuneControllerIntegrationTest'
./gradlew test
./gradlew spotlessCheck
```

`verify-migration.ps1` successfully applied the initial Flyway DDL to a real
PostgreSQL Testcontainers database after Docker Desktop was started.

Recent community admin review work added admin memo list/detail plus manual hide/restore APIs.

- `hidden_reason_type` now includes `admin_hidden` for operator-initiated hides; admins submit a review reason and the server records that reason in audit log metadata.
- `community_memo.reviewed_at` records the latest admin review timestamp alongside `reviewed_by`.
- Admin community APIs use the existing admin JWT flow under `/api/v1/admin/community/memos`.
- Admin list/detail include hidden memos while still excluding soft-deleted memos by default.
- Hide/restore updates `reviewed_by` and does not run FIFO, moderation, or MinIO/file/artifact/gallery mutation.
- Admin community list supports `reported=true/false` filtering, admin detail embeds latest-first report history including `reasonDetail`, and admins can still inspect paged report history through `GET /api/v1/admin/community/memos/{memoId}/reports` without mutating memo/report state.
- Community memo query performance now has Flyway V12 indexes for public visible-wall ordering, FIFO expiry scans, admin list filters, and memo report history lookups.
- Admin community keyword search escapes SQL `LIKE` wildcard characters so `%` and `_` are treated as literal search text.
- Common query performance now has Flyway V13 indexes for active gallery ownership lookups, artifact source-room result scans, CS inquiry admin list filters, and GMS prompt list/latest lookups.
- Admin keyword searches for community memos, CS inquiries, GMS prompts, and system parameters now escape SQL `LIKE` wildcard characters consistently.

## Next Suggested Steps

- Use `backend/docs/codex-prompt-templates.md` for the first feature request.
- Read the relevant product spec before designing API, DB, or event behavior.
- Add ADRs when package structure, database strategy, authentication strategy, or testing strategy changes.
- Use subagents only when explicitly requested for investigation, review, or parallel work.
- Consider enabling a weekly Codex automation for harness health checks after the team agrees on schedule.
