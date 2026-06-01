# Codex Current State

Last updated: 2026-06-01

## Current Focus

- k6 load-test scripts now cover the recent gallery query, infinite canvas
  active-room lookup, fortune external I/O transaction-boundary, and admin
  inquiry SMTP transaction-boundary optimizations. The execution guide is in
  `backend/docs/performance/k6-load-testing-ko.md`, and the 2026-06-01 local
  controlled run results are summarized in
  `backend/docs/performance/k6-load-test-results-ko.md`.
- Gallery list query now reduces unnecessary subtype joins. Count uses only
  `gallery + artifact`, and list lookup first selects page items before joining
  subtype artifact tables. Performance evidence is documented in
  `backend/docs/performance/gallery-query-optimization-ko.md`.
- Fortune create/today requery and admin inquiry reply no longer keep external
  I/O inside a method-wide transaction. `FortuneTransactionSupport` now wraps
  short DB read/write sections, while GMS, card upload, and SMTP send execute
  outside the transaction boundary. Performance evidence is documented in
  `backend/docs/performance/transaction-external-io-optimization-ko.md`.
- Infinite canvas performance visualizations were revalidated against the
  current code shape on 2026-06-01. The docs now reference
  `InfiniteCanvasEditingUseCase` and `InfiniteCanvasOperationApplier` for
  operation apply optimization, and the benchmark SVG assets were regenerated.
- Backend agent harness has been prepared for the `backend/` Spring Boot module.
- The harness now reflects the intended backend stack: Spring Boot, Java, PostgreSQL, Redis, MinIO, and Flyway.
- Team contribution and backend MR conventions are recorded for shared workflow.
- 커뮤니티 메모 상세 공유는 `POST /api/v1/community/memos/{memoUuid}/share`를 사용해
  QR 합성 이미지가 포함된 `ShareCreateResponse`를 생성하며, 캐시 객체는
  `community-memo-shares/{memoUuid}/result-qr-v2.jpg`에 저장한다. 이 흐름은
  `Anonymous-User-UUID`를 검증하고, 존재하는 사용자는 누구나 공개 visible 메모를
  공유할 수 있으며, hidden/deleted/blocked 메모는 거부한다. 이미지 선택은
  `body_image_url`을 우선 사용하고 없으면 `thumbnail_image_url`로 fallback 하며,
  QR 합성 대상은 MinIO 오브젝트 키 기반 이미지로 제한한다.
- The first real backend feature API now includes anonymous user UUID issuance through `POST /api/v1/users/anonymous`.
- Community memo sharing now preserves flipbook animation: visible memos with
  `artifact.kind=flipbook` or `playback_image_reference` use the flipbook GIF as
  the QR composition source, cache the result under
  `community-memo-shares/{memoUuid}/result-qr-v2.gif`, and return the public GIF URL
  in the existing `ShareCreateResponse` shape.
- Backend runtime now sets the JVM default timezone from `nemonic.time-zone`
  (`APP_TIME_ZONE`, default `Asia/Seoul`) during application startup so
  `LocalDateTime.now()` based DB writes and API responses follow the Korean
  service timezone consistently.
- Backend Gradle tests now start the test JVM with
  `user.timezone=Asia/Seoul` so CI date/time assertions stay aligned with the
  Korean service timezone even when the Jenkins host uses UTC.
- Backend now exposes Micrometer/Prometheus metrics for Grafana overview
  panels: `nemonic_ws_active_sessions`, `nemonic_ws_connect_total`,
  `nemonic_ws_disconnect_total`, and `nemonic_content_active_rooms` tagged by
  `content_type=relay|flipbook`. WebSocket active sessions are tracked by STOMP
  session id, and content active rooms count Redis `WAITING`, `PLAYING`, and
  `FINALIZING` rooms while excluding `FINISHED`/`CLOSED`.
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
- Artifact QR download/share now supports `artifact.kind=phone` gallery items by
  using `phone_artifact.phone_image_url` first, falling back to
  `artifact.thumbnail_url`, and producing the same JPG QR asset used by other
  static image artifacts.
- Gallery list/detail and relay result APIs now convert stored MinIO object keys into browser-renderable public URLs through `global.storage.minio.MinioPublicUrlResolver`, while preserving already absolute URLs as-is and keeping the database storage model object-key based.
- Files API calls (`POST /api/v1/files/presign`, `POST /api/v1/files/{fileId}/confirm`, `DELETE /api/v1/files/{fileId}`) also use `Anonymous-User-UUID`.
- Files presign/confirm keeps the direct-upload contract: the frontend PUTs
  bytes to MinIO, while the backend validates metadata and object existence but
  does not decode images or add a white background.
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
  environment-driven through `MAIL_*` variables. `MAIL_FROM` falls back to
  `MAIL_USERNAME`, and SMTP configuration/delivery failures now return 503
  without resolving the inquiry.
- Backoffice admins can now change a customer inquiry status through
  `PATCH /api/v1/admin/inquiries/{inquiryId}/status`; the API accepts
  `new`, `in_progress`, `resolved`, and `closed`, updates only `status` and
  `updatedAt`, and leaves reply fields such as `assignedTo`, `responseNote`,
  and `respondedAt` untouched.
- The client log ingest endpoint `POST /api/v1/logs/client` accepts standard
  frontend log events and emits valid entries to stdout through `logs.client`.
  It applies per-IP in-memory rate limits, payload/event-count limits,
  Origin/Referer allow-list checks, bot drops, event allow-list routing, schema
  drops, and server-side PII sanitization before logging.
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
- Backoffice roles now include `super_admin`, `admin`, and read-only `viewer`.
  Viewer accounts can authenticate and inspect protected backoffice screens, but
  service-layer authorization rejects create/update/delete/force-close/test
  workflows with 403.
- Backoffice super admins can now create `admin` or `viewer` accounts with
  `POST /api/v1/admins`; the API stores BCrypt password hashes, defaults an
  omitted `role` to `admin`, rejects `super_admin` creation through the API, and
  rejects duplicate `login_id` values.
- Authenticated backoffice admins can now list and inspect active admins with
  `GET /api/v1/admins` and `GET /api/v1/admins/{adminId}`.
- Backoffice super admins can now change admin/viewer passwords with
  `PATCH /api/v1/admins/{adminId}`; the API updates the BCrypt password hash,
  revokes the target account's refresh tokens, and blocks previously issued
  access tokens.
- Backoffice super admins can now soft-delete admin/viewer accounts with
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
- Backoffice admins can preview candidate fortune GMS prompt templates through
  `POST /api/v1/backoffice/gms/prompts/preview`; the API calls GMS with the
  unsaved prompt body and sample saju, validates the same fortune result shape
  used by public fortune creation, renders a PNG card as a base64 data URL, and
  does not write `gms_prompt_template`, `artifact`, `fortune_artifact`,
  `gallery`, or MinIO objects.
- Backoffice GMS prompt management now distinguishes saved non-deleted prompts
  from the one currently used prompt. Flyway V16 adds
  `gms_prompt_template.is_active`, activation metadata, a
  `gms_prompt_feature_state` row-lock table, and a partial unique index so only
  one non-deleted active prompt can exist per `feature_type`.
- `POST /api/v1/backoffice/gms/prompts/{promptId}/activate` serializes
  activation by feature type through `gms_prompt_feature_state` and atomically
  deactivates the previous prompt before activating the selected prompt.
  `GET /api/v1/backoffice/gms/prompts/current?featureType=fortune` returns the
  active DB prompt or the built-in fortune fallback prompt when no DB prompt is
  active. `POST /api/v1/backoffice/gms/prompts/{promptId}/test` tests a saved
  prompt with sample saju without persisting artifacts.
- Backoffice `viewer` accounts can read GMS prompt list/detail/current endpoints
  but cannot create, preview/test, update, delete, or activate prompts.
- Backoffice admins can now list system parameters through
  `GET /api/v1/backoffice/system-parameters`; the API requires an admin JWT,
  reads existing `backoffice_setting` rows sorted by `setting_key ASC`,
  supports optional `keyword` search on `setting_key`, parses
  `setting_value` JSON text into the response `value`, and Flyway V8 seeds
  initial backoffice setting rows without changing the schema.
- Backoffice `viewer` accounts can read system parameters but cannot update
  parameter values.
- Backoffice admins can now manage active relay drawing rooms through
  `GET /api/v1/backoffice/relay-rooms` and
  `DELETE /api/v1/backoffice/relay-rooms/{roomCode}`; delete requires an admin
  JWT, closes any non-CLOSED Redis room through CAS, returns `roomCode`, rejects
  already CLOSED rooms with 409, emits `ROOM_CLOSED`, and leaves MinIO,
  artifact, and gallery cleanup out of scope.
- Backoffice `viewer` accounts can list active rooms/canvases across relay,
  flipbook, and infinite canvas, but cannot force-close or delete them.
- Backoffice admins can now list active flipbook rooms through
  `GET /api/v1/backoffice/flipbook-rooms`; the API requires an admin JWT,
  scans Redis `flipbook:room:{roomCode}` state, returns CLOSED-excluded
  WAITING/PLAYING/FINISHED rooms with `roomCode`, `status`, participant count,
  current/total round, and `gameStartedAt`, supports `status`, `page`, and
  `size`, and keeps database/artifact/gallery lookup out of scope.
- Backoffice admins can now delete active flipbook rooms through
  `DELETE /api/v1/backoffice/flipbook-rooms/{roomCode}`; delete requires an
  admin JWT, closes any non-CLOSED Redis room through CAS, returns `roomCode`,
  rejects already CLOSED rooms with 409, syncs invite metadata, emits
  `ROOM_CLOSED`, and leaves MinIO, artifact, gallery, and DB rows untouched.
- Backoffice admins can now query OpenSearch logs through
  `POST /api/v1/admin/logs/search`, `/histogram`, and `/field-summary`.
  The backend maps whitelisted logical indexes to OpenSearch index patterns,
  rejects unsafe query/body/field/time-range inputs, calls OpenSearch through
  WebClient, returns log-search DTOs directly, and emits `admin_logs_query`
  stdout audit events with `service=backoffice-api` and `metadata.actor_id`.
- Swagger/OpenAPI declares JWT bearer authentication for protected admin APIs,
  so Swagger UI can send `Authorization: Bearer <token>` through the global
  Authorize flow.
- Admin login, failed login, and logout events emit structured JSON audit logs
  to stdout using the `08-observability.md` audit schema, with no RDB audit log
  table.
- Admin account creation and deletion also emit `admin_account_create` and
  `admin_account_delete` stdout JSON audit logs after successful service
  transactions; the application still does not write directly to Kafka or
  OpenSearch.
- Admin community memo hide/restore now emit `memo_soft_delete` and
  `memo_restore` stdout JSON audit logs after successful service transactions;
  bulk memo review and `report_review_decided` remain pending because no
  current admin API exists for those operations.
- `admin_user.login_id` is made unique through Flyway V5.
- `admin_role_type` includes `viewer` through Flyway V17.
- Room code generation is available through `RoomCodeGenerator`, producing 6-character uppercase human-readable codes and supporting repository-backed collision checks with `generateUnique(...)`.
- Relay room creation now uses `POST /api/v1/relay/rooms`, reuses `Anonymous-User-UUID`, requires a non-default nickname before room creation, stores the WAITING room state only in Redis under `relay:room:{roomCode}` with a 24-hour TTL, creates the host participant with `connected=false` until WebSocket CONNECT succeeds, and creates no PostgreSQL artifact/gallery rows.
- Relay room state lookup now uses `GET /api/v1/relay/rooms/{roomCode}`, reads the Redis room snapshot without mutation, sorts participants by `joinOrder`, and computes viewer join/reconnect eligibility from the requested `Anonymous-User-UUID`.
- Relay room join/reconnect now uses `POST /api/v1/relay/rooms/{roomCode}/participants`, applies Redis `WATCH`/`MULTI`/`EXEC` optimistic conditional updates for new WAITING-room participants with `connected=false`, keeps WAITING-room REST re-entry open without a reconnect grace cutoff unless the UUID was kicked, validates the current backoffice `relay.reconnect_grace_seconds` setting only for PLAYING rooms without setting `connected=true`, retries short-lived write conflicts, and remains free of PostgreSQL artifact/gallery, MinIO, and WebSocket side effects.
- Relay room WebSocket lobby connections use the STOMP endpoint `/ws/relay`, CONNECT headers `roomCode` and `Anonymous-User-UUID`, topic `/topic/relay/rooms/{roomCode}`, user queue `/user/queue/relay/rooms/{roomCode}`, Redis `connected`/`disconnectedAt` updates where successful CONNECT is the only path to `connected=true` and DISCONNECT returns it to `false`, applies the reconnect grace cutoff only in PLAYING rooms, keeps WAITING-room reconnection available without a time cutoff, session-id-scoped duplicate-session close events, and common `global.websocket` infrastructure for single-server in-memory active session tracking.
- Relay drawing submissions now advance the Redis room state from `FACE` to `BODY` and `BODY` to `LEGS` when every assignment in the current part is `SUBMITTED` or `AUTO_SUBMITTED`; completing `LEGS` moves the room to `FINALIZING`, emits `ALL_PARTS_COMPLETED`, and asynchronously triggers one finalization attempt while keeping the scheduler as the retry/recovery path.
- Relay timeout auto-submit now scans `PLAYING` Redis rooms only after `partDeadlineAt + auto-submit-grace-ms`, marks remaining current-part `PENDING` assignments as `AUTO_SUBMITTED` empty entries without MinIO upload, reuses the shared part advancement flow, and emits `PART_AUTO_SUBMITTED` plus existing transition events after successful CAS saves.
- Relay disconnect-grace auto-submit now uses the same finalization trigger policy as user submission and timeout paths: if dropped-participant auto-submit completes the last `LEGS` assignment, it emits `ALL_PARTS_COMPLETED` and schedules one asynchronous finalization attempt.
- Relay timeout processing now emits a one-time `PART_TIME_UP` WebSocket
  event during the `partDeadlineAt` to
  `partDeadlineAt + auto-submit-grace-ms` window for rooms with pending
  current-part assignments. The event includes `partDeadlineAt`,
  `submitGraceDeadlineAt`, `autoSubmitGraceMillis`, and the current part's
  pending submission list, while the backend remains responsible for fallback
  auto-submit after the grace window.
- Relay drawing submissions now acquire assignment-scoped Redis submit-in-progress locks before file upload; the timeout scheduler skips locked pending assignments until the lock TTL expires, preventing deadline-time user submissions from racing against `AUTO_SUBMITTED` fallback processing.
- Relay disconnect grace processing now scans candidate `PLAYING` Redis rooms after the configured reconnect grace, takes the room mutation lock before mutating room state, marks expired disconnected participants as `dropped` with `droppedAt`, blocks dropped UUIDs from REST rejoin and WebSocket reconnect, auto-submits only their current-part unlocked `PENDING` assignments as empty `AUTO_SUBMITTED`, leaves future part assignments pending until that part becomes current, transfers a dropped host to the lowest `joinOrder` connected non-dropped participant when available, and emits `PARTICIPANT_DROPPED`, `HOST_CHANGED`, `PART_AUTO_SUBMITTED`, and existing part transition events only after successful CAS saves.
- Relay finalization now schedules one asynchronous immediate run right after `ALL_PARTS_COMPLETED`, then still scans `FINALIZING` Redis rooms every 10 seconds after a short ready delay for failure, lock-busy, server-restart, or partial-success recovery. The submission/timeout/disconnect-grace flows do not wait for finalization to finish. Both paths acquire the room-scoped `relay:room-finalization-lock:{roomCode}` lock with a 120-second default TTL, create a UUID attempt id, store attempt object-key markers under `relay:room-finalization-attempt:{roomCode}:{attemptId}` for 24 hours by default, compose one alpha-preserving vertical FACE/BODY/LEGS PNG per `canvasIndex`, store final original and thumbnail objects under `relay/results/{artifactId}/`, write matching `artifact`, `relay_drawing_artifact`, and participant gallery rows, mark the Redis room `FINISHED`, and emit `RESULT_CREATED`; presigned result URLs remain follow-up work.
- Relay finalization async executor values are configurable under `nemonic.relay.finalization.async.*`; defaults are core pool size `2`, max pool size `4`, queue capacity `100`, thread prefix `relay-finalization-`, shutdown task waiting enabled, and a 30-second await-termination window.
- Relay final result composition supports `nemonic.relay.finalization.overlap-height`, currently defaulted to `120` px to match the frontend hint area. A value of `0` preserves the previous vertical composition. Positive values overlap FACE/BODY and BODY/LEGS by that many pixels, clamp excessive overlap safely, and render with layer priority `FACE > BODY > LEGS`. Hint images are still not composed directly; the frontend must submit drawing images that already include matching overlap hint areas.
- Relay final result composition no longer paints a white background. Final
  original and resized thumbnail PNGs use ARGB images so uploaded transparent
  PNG alpha survives; missing part and blank canvas regions remain transparent.
- Relay finalization idempotency now checks existing `artifact.source_room_id = roomCode` relay results before creating new files. If DB result rows already match the expected canvas indexes after a previous Redis transition failure, the retry skips MinIO upload/DB insert, retries only the Redis `FINISHED` transition, clears retry state, and logs `relay_finalization_recovered`.
- Relay result lookup now uses `GET /api/v1/relay/rooms/{roomCode}/results`, reads PostgreSQL `artifact`/`relay_drawing_artifact`/active `gallery` rows as the source of truth, returns final combined/thumbnail URLs plus canvasIndex FACE/BODY/LEGS drawer metadata parsed from `artifact.meta`, and succeeds even after Redis room state expires when DB result ownership exists.
- Relay room close now scans `FINISHED` Redis rooms after `updatedAt + close-delay` and also supports host-triggered `POST /api/v1/relay/rooms/{roomCode}/close`; both paths mark eligible rooms `CLOSED` through CAS, sync invite metadata, emit `ROOM_CLOSED` with additive `closeReason`, and best-effort close same-server active WebSocket sessions only on the successful state transition, while artifact/gallery deletion remains out of scope.
- Relay abandoned-room cleanup now scans `WAITING` rooms where every participant has been disconnected for 5 minutes and `PLAYING` rooms where every participant has been disconnected or dropped for 5 minutes, closes them through the shared CAS close command, syncs invite metadata, emits `ROOM_CLOSED`, and logs `close_reason=waiting_idle_timeout` or `playing_abandoned`.
- Relay connection reconciliation now scans `WAITING`/`PLAYING` Redis rooms whose participants still have `connected=true`, compares them against the same-server relay `WebSocketSessionRegistry`, and CAS-corrects missing sessions to `connected=false` with `disconnectedAt` set to the reconciliation time; it logs `relay_room_recovered_or_reconciled` without publishing `PARTICIPANT_DISCONNECTED`.
- Relay abandoned-room cleanup also closes abnormal `WAITING` rooms with an empty participant list using `close_reason=waiting_empty`.
- Relay finalization failure handling now increments Redis key `relay:room-finalization-retry:{roomCode}` with the room-state TTL, retries on the next 10-second scheduler tick, clears the key after successful finalization, and closes the `FINALIZING` room with `close_reason=finalization_failed` on the 60th failed attempt.
- Relay remains scoped to a single backend server for scheduler coordination; no scheduler leader election or scheduler-wide scan lock is currently used. Room mutation locks and the room-scoped finalization lock protect overlapping relay work in the single-server runtime.
- Relay finalization now best-effort deletes result objects created by the current attempt if MinIO upload succeeded but DB result persistence fails before rows are saved; cleanup failures are logged and do not replace the original finalization exception, and existing artifact rows or reused result objects are not deleted.
- Relay temp cleanup now scans `CLOSED` Redis rooms, collects distinct assignment `objectKey` and `hintObjectKey` values only under `relay/tmp/{roomCode}/`, hard-deletes those temporary objects from MinIO, and records cleanup completion with a separate Redis marker plus cleanup lock; its old-temp fallback deletes only 24-hour-old `relay/tmp/` objects whose room state is missing, `FINISHED`, or `CLOSED`.
- Relay Redis room scan repository methods share bounded `SCAN` instrumentation and emit debug-level scan purpose, scanned key count, matched room count, limit, and duration data. State-specific Redis indexes remain a follow-up optimization because they would require index synchronization on every CAS state transition.
- Relay orphan object cleanup now runs hourly by default, scans old `relay/tmp/` and `relay/results/` objects with 24-hour retention and bounded limits, deletes temp objects only for inactive/missing rooms, caches temp room-state lookups within one cleanup run, batch-checks DB result object references before deletion, checks active finalization attempt references in batches, and skips ambiguous result objects instead of risking deletion of persisted artifacts.
- Relay final result composition reuses original PNG bytes as thumbnail bytes when thumbnail resizing is unnecessary; when resizing is needed, the existing separate thumbnail encoding path is preserved while keeping alpha.
- Relay waiting-room host kick now uses `POST /api/v1/relay/rooms/{roomCode}/participants/kick` with `targetUserUuid` in the JSON body, removes only non-host participants while preserving remaining `joinOrder` values, records `kickedUserUuids` in the Redis room state, blocks kicked UUIDs from REST invite/join and WebSocket reconnect paths, and emits `PARTICIPANT_KICKED` plus a best-effort personal `KICKED_FROM_ROOM` queue event before closing the same-server active session.
- Relay waiting-room voluntary leave now uses `DELETE /api/v1/relay/rooms/{roomCode}/participants/me`, removes the caller without adding them to `kickedUserUuids`, preserves remaining `joinOrder` values, transfers host ownership to the lowest remaining `joinOrder` when the host leaves, marks the room `CLOSED` when the last participant leaves, emits `PARTICIPANT_LEFT` plus `HOST_CHANGED` or `ROOM_CLOSED` when applicable, and best-effort closes the leaving user's same-server active WebSocket session.
- Relay service internals are grouped under `service.room`, `service.game`, `service.assignment`, `service.submission`, `service.timeout`, `service.finalization`, `service.close`, `service.cleanup`, and `service.support`, while `RelayRoomService` and `RelayRoomServiceImpl` remain the controller-facing facade.
- Flipbook room creation now uses `POST /api/v1/flipbook/rooms`, reuses `Anonymous-User-UUID`, requires a non-default nickname before room creation, stores the WAITING room state only in Redis under `flipbook:room:{roomCode}` with a 24-hour TTL, and stores matching common invite metadata under `invite:{roomCode}` with `boothType=flipbook`.
- Flipbook room state lookup now uses `GET /api/v1/flipbook/rooms/{roomCode}`, reads the Redis room snapshot without mutation, sorts participants by `joinOrder`, and computes viewer participation, host, joinable, startable, and blocked-reason flags from the requested `Anonymous-User-UUID`.
- Flipbook waiting-room host kick now uses `POST /api/v1/flipbook/rooms/{roomCode}/kick` with `targetUserUuid` in the JSON body, removes only non-host participants while preserving remaining `joinOrder` values, records `kickedUserUuids` in the Redis room state, blocks kicked UUIDs from invite re-entry and WebSocket reconnect paths, and emits `PARTICIPANT_KICKED` plus a best-effort personal `KICKED_FROM_ROOM` queue event before closing the same-server active session.
- Flipbook game start now uses `POST /api/v1/flipbook/rooms/{roomCode}/start`, requires the caller to be the host of a WAITING room, requires at least two connected WebSocket participants, calculates `totalRounds` from the `flipbook.min_frames_per_flipbook` runtime setting snapshot, stores `currentRound`, `totalRounds`, round deadline, `gameStartedAt`, and generated frame assignments in Redis, syncs invite TTL metadata, and emits `GAME_STARTED`.
- Flipbook current assignment lookup now uses `GET /api/v1/flipbook/rooms/{roomCode}/assignments/me`, requires the caller to be a non-dropped participant in a PLAYING room, returns the current round assignment, remaining seconds, and previous-frame hint metadata when a submitted/auto-submitted previous frame exists.
- Flipbook PLAYING-room re-entry now applies a 10-second reconnect grace period to both common invite re-entry and WebSocket CONNECT; the frontend should call invite and immediately open WebSocket, and either path returns the reconnect-expired 409 once `disconnectedAt + 10s` has passed.
- Flipbook timeout processing now emits a one-time `ROUND_TIME_UP` WebSocket
  event during the `roundDeadlineAt` to
  `roundDeadlineAt + auto-submit-grace-ms` window for rooms with pending
  current-round assignments. The event includes `roundDeadlineAt`,
  `submitGraceDeadlineAt`, and `autoSubmitGraceMillis` so the frontend can
  export the current canvas and call the normal frame submit API before the
  backend fallback auto-submit runs. A separate Redis marker key prevents
  duplicate `ROUND_TIME_UP` events for the same room, round, and deadline.
- Flipbook frame submission now accepts requests until
  `roundDeadlineAt + auto-submit-grace-ms`, keeping the default two-second
  grace window aligned with timeout fallback auto-submit.
- Flipbook frame submission now also emits `ROUND_STARTED` whenever a normal
  submit advances to the next round, and emits `ALL_ROUNDS_COMPLETED` when the
  last round completes. Frontend screen transitions can therefore use
  `ROUND_STARTED` for every next-round start and `ALL_ROUNDS_COMPLETED` for game
  completion, regardless of whether the round ended by manual submission or
  timeout auto-submit.
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
- Community memo creation now uses `POST /api/v1/community/memos` with required `Anonymous-User-UUID`, supports `sourceType=DIRECT` and `sourceType=GALLERY`, requires distinct confirmed `COMMUNITY` `originalFileId` and `thumbnailFileId`, links GALLERY posts to an owned active gallery artifact only for source attribution, runs pre-publication moderation with the confirmed upload public URLs before insert, stores the uploaded original and thumbnail object keys directly in `community_memo.body_image_url` and `community_memo.thumbnail_image_url`, and applies 50-visible-memo FIFO soft deletion with `deleted_reason=expired`.
- Community memo creation does not decode image bytes, create `community/memos/{memoId}/...` derivatives, or run white-key background removal. Frontend direct drawings must export transparent PNGs when transparency is needed; gallery-origin relay/flipbook result images follow their result-composition alpha preservation policy. White-key removal was removed because it can erase intentional white strokes, text, highlights, and decoration. Complex photo cutout or AI segmentation remains a separate opt-in follow-up candidate, and no DB migration is needed because existing memo image columns continue to store object keys.
- Community memo layout updates now use `PATCH /api/v1/community/memos/{memoId}` with required `Anonymous-User-UUID`; only the owner of a visible memo can update `position_x`, `position_y`, `z_index`, `rotation_deg`, and `updated_at`, while image keys, artifact linkage, decoration, moderation fields, `attached_at`, and FIFO state remain untouched.
- Community memo deletion now uses `DELETE /api/v1/community/memos/{memoId}` with required `Anonymous-User-UUID`; only the owner of a visible memo can soft delete it with `deleted_reason=user_delete`, while MinIO files, file_upload rows, artifact/gallery links, moderation data, and FIFO restoration state remain untouched.
- Community memo reporting now uses `POST /api/v1/community/memos/{memoId}/reports` with required `Anonymous-User-UUID`; visible non-owned memos can be reported once per user with Korean report categories plus optional nullable `reasonDetail`, and reaching `community.report_hide_threshold` automatically hides the memo with `hidden_reason=report_threshold` without invoking FIFO or moderation. The default threshold is 5 reports.
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

Recent artifact QR download/share work adds `GET /api/v1/artifacts/{artifactId}/download` and
`POST /api/v1/artifacts/{artifactId}/share`.

- Both APIs verify the caller's active `gallery` ownership through `ArtifactImageUrlRepository`.
- Download/share artifact kinds are currently `relay_drawing`, `flipbook`, `fortune`, `infinite_canvas`,
  `phone`, and `community_memo`.
- QR URLs use a DB-free signed share token route, `/share/{shareToken}`, with artifact id, artifact kind, and channel in the signed payload. The token intentionally excludes owner user id so the same artifact QR asset can be reused by all owners.
- QR composition uses ZXing low error correction (`L`), one-module margin, and a larger bounded overlay size so download/share QR modules render less densely on static images and GIF frames.
- The API creates or reuses a QR-composed MinIO cache object, then returns JPG/GIF bytes as an attachment.
- Still images are cached as JPG under `artifact-downloads/{artifactId}/result-qr-v2.jpg`; flipbook GIFs are cached as `artifact-downloads/{artifactId}/result-qr-v2.gif` with QR overlaid on every frame.
- `POST /api/v1/artifacts/{artifactId}/share` reuses the same QR cache and returns the public QR image URL plus Kakao/Instagram UTM URLs in the existing `ShareCreateResponse` shape.
- Community memo QR assets read `community_memo.body_image_url` first, then `community_memo.thumbnail_image_url`, and only fall back to `artifact.thumbnail_url`.
- The older galleryId-based `POST /api/v1/share` endpoint was removed; use artifact or community memo
  share endpoints instead.

```bash
./gradlew --no-daemon test --tests com.nemonicworld.artifact.service.download.ArtifactDownloadServiceImplTest --tests com.nemonicworld.artifact.service.share.ArtifactShareServiceImplTest --tests com.nemonicworld.artifact.controller.ArtifactControllerIntegrationTest --tests com.nemonicworld.artifact.controller.ArtifactOpenApiIntegrationTest
```

Recent flipbook result work aligns room completion with the relay finalization model.

- Last-round completion now changes Redis room status to `FINALIZING`, and `ALL_ROUNDS_COMPLETED` WebSocket events carry `roomStatus=FINALIZING`.
- `FlipbookRoomFinalizationScheduler` scans `FINALIZING` rooms, acquires a room-scoped Redis finalization lock, creates GIF/thumbnail results under `flipbook/results/{artifactId}/`, stores `artifact` + `flipbook_artifact` + gallery rows for non-dropped participants, then changes the room to `FINISHED`.
- Flipbook result generation no longer paints transparent frame backgrounds
  white. GIF frames are normalized through ARGB images before writing while
  keeping the existing `image/gif` contract, and thumbnail PNG resize uses ARGB
  so transparent frame backgrounds remain transparent.
- Flipbook GIF frame metadata now uses `restoreToBackgroundColor` disposal
  instead of `none`, so transparent submitted frames are displayed
  independently instead of accumulating over previous frames in GIF players.
- After finalization completes, the backend emits a `RESULT_CREATED` WebSocket event with artifact IDs and per-`flipbookIndex` object keys.
- `GET /api/v1/flipbook/rooms/{roomCode}/result` is now a read-side API: existing artifact/gallery rows return `ready=true`; while result generation is pending or inconsistent, the API returns `ready=false` instead of lazily creating GIFs.
- Flipbook game start uses `totalRounds=flipbook.min_frames_per_flipbook` from the runtime settings snapshot, so assignment count is `participantCount * totalRounds`.
- Flipbook finalization excludes empty/auto-submitted frames from GIF output. If no renderable result frame remains, it skips artifact/gallery creation, changes the room to `CLOSED`, and emits `ROOM_CLOSED` with `closeReason=no_result_frames`.

```bash
GRADLE_USER_HOME=.gradle-user-home ./gradlew spotlessCheck test --tests 'com.nemonicworld.flipbook.*' --no-daemon
```

Recent flipbook room mutation work aligned Redis state-change contention handling with relay.

- `FlipbookRoomMutationLockRepository` uses `flipbook:room-mutation-lock:{roomCode}` with token-checked Lua release.
- Frame submission acquires the room mutation lock before latest-state mutation/CAS save, while keeping duplicate submitted-frame lookup idempotent.
- Timeout auto-submit and disconnect-grace scans precheck candidates, skip when the room lock is busy, and release the lock after event publication.
- The lock protects shared `flipbook:room:{roomCode}` updates; existing Redis CAS retries remain as a final guard.
- Flipbook auto-submit grace defaults to 5 seconds, and submit API processing now also uses an assignment-scoped submission lock.
- Timeout auto-submit skips PENDING assignments that are currently protected by a flipbook submission lock.
- Disconnect-grace now mirrors relay more closely: dropped participants' current PENDING frame assignments are auto-submitted immediately unless a submission lock is active, and round advancement/result finalizing events are published from that update.
- `ROUND_TIME_UP` WebSocket payload now includes `pendingCount` and current-round `pendingSubmissions` with `flipbookIndex`, `frameIndex`, user UUID/nickname, and connected status.
- FINISHED flipbook rooms now mirror relay's runtime lifecycle: `FlipbookRoomCloseScheduler` scans rooms past `nemonic.flipbook.close.delay-seconds`, transitions them to CLOSED through Redis CAS, syncs invite metadata, and publishes `ROOM_CLOSED`.
- Flipbook abandoned-room cleanup closes empty WAITING rooms, idle WAITING rooms, and PLAYING rooms where every participant is disconnected or dropped, publishing `ROOM_CLOSED` with `closeReason` such as `waiting_empty`, `waiting_idle_timeout`, or `playing_abandoned`.
- `FlipbookRoomEventLogger` now mirrors relay's structured logging wrapper and emits flipbook business/warn events for room lifecycle, WebSocket connect/disconnect, submission, timeout, disconnect-grace, finalization, and close flows.
- Invite-code new joins now distinguish PLAYING rooms from closed rooms: relay and flipbook return `게임이 진행 중입니다.` for in-progress games and keep `이미 종료된 방입니다.` for non-waiting terminal states.

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

Recent fortune card image rendering work fixed Korean glyph fallback in gallery thumbnails.

- `FortuneCardRenderer` now loads bundled `fonts/NanumGothic-Regular.ttf` with
  `Font.createFont(...)`, registers it with AWT, and derives all card text fonts
  from that base font instead of relying on `Font.SANS_SERIF`.
- The runtime Docker image also installs `fontconfig` and `fonts-nanum` as a
  container-level fallback for AWT/fontconfig.
- New renderer tests verify the bundled font can display Korean fortune text and
  that Korean-content cards are emitted as readable 900x1200 PNGs.

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

Recent backoffice audit log emit work aligned remaining operator mutation APIs with the observability spec and the stdout -> Fluent Bit -> Kafka `logs.audit` -> OpenSearch `audit-logs-*` pipeline.

- CS inquiry mutations emit `inquiry_status_change` and `inquiry_reply_send` after successful transaction commit. Audit snapshots include only status/assignee metadata, not inquiry body, reply body, email address, or attachments.
- GMS prompt create/update/delete emit the documented `prompt_update` event with `metadata.action` set to `create`, `update`, or `delete`. Prompt body text is excluded; updates only flag `content_changed`.
- System parameter bulk update emits `param_change` with `target_id=bulk:<count>`. Safe `before`/`after` values are keyed by parameter name, and sensitive parameter keys such as password/secret/token/webhook/SMTP/API-key values are redacted.
- Relay and flipbook backoffice forced closes emit `relay_room_force_close` and `flipbook_room_force_close` with `target_type=room`, `action=force_close`, and before/after status snapshots.
- Still deferred because current APIs are missing or read-only: `prompt_rollback`, `inquiry_internal_memo`, `infinite_canvas_force_close`, `notification_send`, `electron_channel_change`, `electron_release_publish`, `memo_bulk_soft_delete`, `memo_bulk_restore`, and `report_review_decided`.
- No Kafka producer, OpenSearch client, Fluent Bit config, audit RDB table, or audit migration was added; backend remains responsible only for one-line JSON emit to stdout.

Recent relay logging work added structured event emission for the relay drawing lifecycle.

- Relay business events now cover room creation/settings/join/leave/kick/host change, WebSocket connect/reconnect/disconnect/reject/duplicate-session close, start/part start/time-up/submission/rejection/auto-submit/drop/all-parts-complete/finalization-immediate-trigger/finalization-attempt-start/finalization-recovery/result-created/room-closed/temp-cleanup-completed/orphan-cleanup-completed.
- Relay operational warning events cover timeout/disconnect/finalization/cleanup failures, finalization attempt failures, orphan cleanup failures, room mutation lock contention, Redis CAS retry exhaustion, and MinIO upload followed by Redis save conflict.
- Relay logging field coverage now includes WebSocket reconnect `session_id`, rejected WebSocket/start/submission room-state fields, disconnect-grace failure `uuid`, stage-specific finalization `artifact_id`, finalization `attempt_id`, orphan cleanup counters, and non-null temp cleanup failure counts.
- Backoffice relay force-close emits `relay_room_force_close` audit metadata and `relay_room_closed` business metadata; relay-scoped system parameter changes emit `param_change`.
- `backend/docs/product-spec/08-observability.md` includes the relay event names in the backend business-event allow-list.

Recent relay runtime settings work connects the seeded backoffice relay parameters to runtime behavior.

- New relay rooms read `backoffice_setting` key `relay.room_participant_limit` and store the resolved min/max values in the Redis room snapshot.
- Missing, blank, malformed, or invalid participant-limit settings fall back to the default `2..6` range and emit a warning log.
- Existing Redis room snapshots keep their stored min/max values after backoffice setting changes; the setting is applied only to newly created rooms.
- Backoffice system parameter bulk updates validate `relay.room_participant_limit` as a JSON object with integer `min`/`max`, `min >= 2`, `max >= min`, and an operational upper bound of 20.
- New relay rooms read `relay.room_time_limit_seconds` and store its `default` value as `timeLimitSeconds`; existing Redis room snapshots keep their current time limit after backoffice setting changes.
- Relay waiting-room setting changes validate requested `timeLimitSeconds` against the latest `relay.room_time_limit_seconds.allowed` list, falling back to `30/45/60` when the setting is missing or invalid.
- Relay room create and state lookup responses now expose `timeLimitDefaultSeconds`, `timeLimitAllowedSeconds`, and `reconnectGraceSeconds` alongside the room's stored `timeLimitSeconds`. WebSocket room-state events expose the same time-limit and reconnect-grace metadata except for viewer-only fields. Frontend screens should display the stored room value, use the allowed list for future waiting-room setting changes, and use `reconnectGraceSeconds` for PLAYING-room reconnect guidance; the current room value is not forced to match a later backoffice allowed-list change.
- Relay REST rejoin, WebSocket reconnect, and disconnect-grace scheduler processing read `relay.reconnect_grace_seconds` on each request or scheduler tick, falling back to 10 seconds when the setting is missing or invalid.
- Relay request flows that need multiple relay runtime settings now use a `RelayRuntimeSettingsSnapshot` loaded through one `backoffice_setting` key batch lookup, then reuse it within the request. No cross-request cache is used, so backoffice changes still apply from the next request or scheduler tick.
- Backoffice validation now rejects invalid relay time-limit objects (`default`, non-empty integer `allowed`, `default` included in `allowed`, 5-600 seconds) and invalid relay reconnect grace objects (`value` 0-300 seconds) before any system parameter row is updated.

Recent backoffice system parameter update work changed the PATCH request contract to typed fields.

- `PATCH /api/v1/backoffice/system-parameters` now accepts named optional fields such as `relayRoomParticipantLimit`, `relayRoomTimeLimitSeconds`, `communityMaxMemoCount`, `flipbookRoomParticipantLimit`, and `fortuneDailyLimit` instead of client-supplied `items[].id`.
- The service maps each included request field to the existing `backoffice_setting.setting_key`, updates only included fields in one transaction, and keeps the existing list-style response and `param_change` audit log.
- The typed request covers the V9 seeded editable settings and validates participant limits, time-limit objects, and positive integer value objects before updating any row.
- The legacy `SystemParameterBulkUpdateRequest` DTO remains in source, but the default controller/OpenAPI PATCH contract is the typed request body.

Recent community runtime settings work connects the seeded backoffice max memo count to FIFO.

- Community memo creation now reads `backoffice_setting` key `community.max_memo_count` before the post-create FIFO check.
- Missing, blank, malformed, or invalid `community.max_memo_count` settings fall back to the default visible memo limit of 50 and emit a warning log.
- Backoffice changes to `communityMaxMemoCount` do not immediately expire existing visible memos; the changed value is applied on the next community memo create/FIFO check.
- The community FIFO business log now records the resolved dynamic `max_visible_memo_count` instead of a hard-coded value.

Recent community logging work reused the shared structured event logger for community canvas and backoffice review flows.

- `StructuredEventLogger` centralizes JSON emission to `logs.api`, `logs.websocket`, and `logs.audit`; the existing relay logger and admin audit logger now delegate to it.
- Community API logs now cover memo list/detail views, create, moderation request/allowed/blocked/failure, FIFO check/expiry, layout update/denial, user delete/denial, report create/rejection, and report-threshold auto hide.
- COMMUNITY-purpose file uploads now emit presign, confirm, and pending-delete events without affecting other file purposes.
- Admin community list/detail/report-history views emit audit events, while existing hide/restore audit logs keep the operator-provided review reason in metadata.
- `backend/docs/product-spec/08-observability.md` includes the community event names in the backend event allow-list.
- Community memo list/detail responses now include `memoPlaybackImageUrl`. Visible GALLERY memos backed by `artifact.kind=flipbook` expose the resolved public `flipbook_artifact.gif_url` to any viewer, while DIRECT and non-flipbook memos return `null`; `GET /api/v1/artifacts/{artifactId}/image-urls` remains owner-scoped.

## Next Suggested Steps

- Use `backend/docs/codex-prompt-templates.md` for the first feature request.
- Read the relevant product spec before designing API, DB, or event behavior.
- Add ADRs when package structure, database strategy, authentication strategy, or testing strategy changes.
- Use subagents only when explicitly requested for investigation, review, or parallel work.
- Consider enabling a weekly Codex automation for harness health checks after the team agrees on schedule.
