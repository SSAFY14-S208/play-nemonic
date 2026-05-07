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

Timeout processing applies only to `PLAYING` rooms whose current part deadline
has expired. It marks current-part `PENDING` assignments as
`AUTO_SUBMITTED`, `empty=true`, with no object keys.

Disconnect grace processing applies only to `PLAYING` rooms. A participant is
marked dropped when disconnected beyond the reconnect grace window. Dropped
participants cannot rejoin or reconnect. Only their current-part pending
assignments are auto-submitted immediately; future assignments stay pending
until that future part becomes current.

If a dropped host has a connected non-dropped candidate, transfer host ownership
to the lowest `joinOrder` candidate. If there is no candidate, keep the current
state safely and do not implement all-dropped room finalization in this step.

## Consequences

- Positive: Scheduler side effects are idempotent enough for repeated scans.
- Positive: User submissions and scheduler auto-submissions race through the
  same Redis CAS boundary.
- Positive: Future dropped assignments are revealed at the natural part time,
  matching the frontend timeline.
- Negative: Uploaded files may briefly remain if MinIO upload succeeds but the
  Redis CAS update later fails.
- Follow-up: Full multi-node scheduler coordination may need stronger locks or
  leader election beyond the existing targeted locks.
