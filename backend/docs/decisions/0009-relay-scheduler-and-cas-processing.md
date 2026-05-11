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

Disconnect grace processing applies only to `PLAYING` rooms. A participant is
marked dropped when disconnected beyond the reconnect grace window. Dropped
participants cannot rejoin or reconnect. Only their current-part pending
assignments are auto-submitted immediately; future assignments stay pending
until that future part becomes current.

Use room mutation locks around scheduler flows that can overlap with user
submissions or other scheduler ticks. The lock guards the latest room-state
read, mutation, and CAS save window; expired or busy locks result in no-op
processing for that tick rather than unsafe concurrent mutation.

If a dropped host has a connected non-dropped candidate, transfer host ownership
to the lowest `joinOrder` candidate. If there is no candidate, keep the current
state safely and do not implement all-dropped room finalization in this step.

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
- Negative: Uploaded files may briefly remain if MinIO upload succeeds but the
  Redis CAS update later fails.
- Follow-up: Full multi-node scheduler coordination may need stronger locks or
  leader election beyond the existing targeted locks.
