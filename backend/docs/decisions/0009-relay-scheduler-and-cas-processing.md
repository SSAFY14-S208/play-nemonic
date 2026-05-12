# 0009. Relay Drawing Scheduler And CAS Processing

Date: 2026-05-06

## Status

Accepted

## Context

Several relay transitions are not direct user commands:

- Part timeout auto-submit.
- Disconnect grace expiration.
- Final result generation.
- Automatic room close after result viewing time.
- Automatic abandoned-room close for stuck lobby/game states.
- Temporary file cleanup.

These jobs can overlap with user submissions, reconnects, manual close, and
other scheduler ticks.

## Decision

Run relay background work through focused scheduler services. Scan Redis with
`SCAN`-based repository methods instead of `KEYS`. Each scheduler tick processes
rooms independently and logs per-room failures without stopping the full scan.

Use Redis optimistic CAS (`saveIfUnchanged`) for room state transitions. Retry
short-lived CAS conflicts where the use case already supports retries. Publish
WebSocket events only after the CAS write succeeds.

Timeout processing applies only to `PLAYING` rooms. When the current part
deadline has expired but is still within the auto-submit grace window, publish a
one-time `PART_TIME_UP` event if the current part still has `PENDING`
assignments. Store a Redis marker keyed by room, part, and deadline so repeated
scans do not duplicate the event. When `partDeadlineAt + auto-submit-grace` has
expired, mark current-part `PENDING` assignments as `AUTO_SUBMITTED`,
`empty=true`, with no object keys.

Normal submissions remain accepted until `partDeadlineAt + auto-submit-grace`.
Assignment-scoped submit locks protect in-progress uploads; timeout and
disconnect-grace processing skip locked assignments and can process them in a
later scan after the lock expires.

Disconnect grace processing applies only to `PLAYING` rooms. The reconnect
grace window is read from backoffice setting
`relay.reconnect_grace_seconds` on each REST rejoin, WebSocket reconnect, and
scheduler tick, with a 10-second fallback if the setting is missing or invalid.
A participant is marked dropped when disconnected beyond the resolved reconnect
grace window. Dropped participants cannot rejoin or reconnect. Only their
current-part pending assignments are auto-submitted immediately; future
assignments stay pending until that future part becomes current.

Use room mutation locks around scheduler flows that can overlap with user
submissions or other scheduler ticks. The lock guards the latest room-state
read, mutation, and CAS save window; expired or busy locks result in no-op
processing for that tick rather than unsafe concurrent mutation.

If a dropped host has a connected non-dropped candidate, transfer host ownership
to the lowest `joinOrder` candidate. If there is no candidate, keep the current
state safely and do not implement all-dropped room finalization in this step.

Close abandoned relay rooms through a separate scheduler:

- `WAITING`: if the room has no participants because of abnormal Redis/runtime
  divergence, close it immediately with `close_reason=waiting_empty`.
- `WAITING`: if the room has at least one participant and every participant has
  `connected=false` for 5 minutes, close it with
  `close_reason=waiting_idle_timeout`.
- `PLAYING`: if the room has at least one participant and every participant is
  either disconnected or dropped for 5 minutes, close it with
  `close_reason=playing_abandoned`.

Both flows scan Redis with `SCAN`, close through the shared active-room CAS
command, sync invite metadata, and publish `ROOM_CLOSED` only after the CAS save
succeeds. They do not auto-submit missing parts or attempt final result
generation.

Run connection reconciliation before abandoned cleanup on an independent
30-second scheduler cadence. It scans only `WAITING` and `PLAYING` rooms whose
participants contain `connected=true`, compares each participant to the
same-server relay `WebSocketSessionRegistry`, and CAS-updates missing sessions to
`connected=false`. CAS conflicts are no-op for that tick. The scheduler logs
`relay_room_recovered_or_reconciled` and lets disconnect-grace or abandoned-close
jobs perform the follow-up state transition in later ticks.

When the last `LEGS` assignment completes through a user submission or timeout
auto-submit, the successful `FINALIZING` CAS write emits `ALL_PARTS_COMPLETED`
and then triggers one immediate finalization attempt in the same processing
flow. This improves the normal user path without changing retry behavior. The
immediate attempt does not loop on failure; failed attempts are recorded and the
30-second scheduler remains responsible for later retries, server-restart
recovery, lock-busy recovery, and partial-success recovery.

Finalization processing uses a room-scoped Redis lock,
`relay:room-finalization-lock:{roomCode}`, with a 120-second default TTL. Lock
acquisition failure is a no-op for that scheduler tick or immediate trigger and
logs `relay_finalization_lock_skipped`. After the lock is acquired, the worker
creates a finalization attempt id and stores a 24-hour attempt marker under
`relay:room-finalization-attempt:{roomCode}:{attemptId}` so uploaded result
object keys can be tied to the current attempt.

Finalization retries use a separate Redis counter key,
`relay:room-finalization-retry:{roomCode}`, with the same 24-hour TTL as the
room state. The finalization scheduler runs every 30 seconds by default. Each
failed finalization tick increments the counter and logs `retry_count`,
`max_retry_count`, and `attempt_id`. A successful finalization clears the retry
counter. On the 20th failure, the room is closed with
`close_reason=finalization_failed`, invite metadata is synced, and `ROOM_CLOSED`
is published.

If a finalization attempt saved PostgreSQL result rows but failed to update the
Redis room to `FINISHED`, a later retry first checks existing
`artifact.source_room_id = roomCode` rows. When the stored result count and
canvas indexes match the room assignments, the worker skips new uploads and DB
inserts, retries only the Redis `FINISHED` transition, and logs
`relay_finalization_recovered`.

Run old relay object cleanup through a separate hourly scheduler. It scans
`relay/tmp/` and `relay/results/` with a 24-hour default retention and a bounded
scan limit. Temp objects are deleted only when their room state is missing or is
already `FINISHED`/`CLOSED`. Result objects are deleted only when they are not
referenced by DB result columns and are not listed in an active finalization
attempt marker. Ambiguous result objects are skipped.

## Consequences

- Positive: Scheduler side effects are idempotent enough for repeated scans.
- Positive: User submissions and scheduler auto-submissions race through the
  same Redis CAS boundary.
- Positive: Clients receive a server-timed deadline event before fallback
  auto-submit, while the backend still owns the authoritative timeout result.
- Positive: Submit locks avoid overwriting an upload that is already in
  progress at the deadline.
- Positive: Future dropped assignments are revealed at the natural part time,
  matching the frontend timeline.
- Positive: WAITING, PLAYING, and FINALIZING rooms no longer remain in
  backoffice active-room lists indefinitely when all users leave or finalization
  keeps failing.
- Positive: A server restart no longer leaves stale relay `connected=true`
  values that can permanently block abandoned-room cleanup.
- Positive: The normal final-result path no longer waits for the next
  30-second scheduler tick, while the scheduler still owns retry and recovery
  after immediate-trigger failure.
- Positive: Finalization attempt ids make retry, recovery, and result object
  cleanup logs traceable for a single run.
- Positive: A Redis `FINISHED` transition conflict after DB save can be
  recovered without duplicate MinIO uploads or duplicate DB result rows.
- Positive: Old orphan cleanup reduces long-lived storage drift while protecting
  active-room temp files and persisted result files.
- Negative: Orphan cleanup is intentionally conservative; result objects with
  uncertain references are skipped and may need manual investigation.
- Follow-up: Full multi-node scheduler coordination may need stronger locks or
  leader election beyond the existing targeted locks.
