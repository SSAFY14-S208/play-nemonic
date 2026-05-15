# 0010. Relay Drawing File Lifecycle And Finalization

Date: 2026-05-06

## Status

Accepted

## Context

Relay drawing uses uploaded part images while a game is running, but the final
result screen and gallery need durable final images. Part images are temporary
composition inputs and hints; final result images are product artifacts.

## Decision

Store submitted part images and hint images in MinIO under a temporary relay
prefix:

```text
relay/tmp/{roomCode}/{canvasIndex}/{part}.png
relay/tmp/{roomCode}/{canvasIndex}/{part}-hint.png
```

Store only object keys in Redis assignments. Empty automatic submissions do not
upload files and store `objectKey=null`, `hintObjectKey=null`.

For in-game assignment lookup, derive a browser-readable hint image URL from the
stored `hintObjectKey` only when the previous part is non-empty:

```text
{nemonic.storage.minio.public-url}/{nemonic.storage.minio.bucket}/{hintObjectKey}
```

The assignment lookup API does not issue presigned URLs and does not check MinIO
object existence. Redis still stores only object keys; the public URL is a
response-time projection for the current game screen. Empty hints and missing
hint object keys return `url=null`.

When all parts are completed, move the room to `FINALIZING`, emit
`ALL_PARTS_COMPLETED`, and schedule one asynchronous immediate finalization
attempt. The submission, timeout, or disconnect-grace processing that completed
the room does not wait for finalization to finish. The finalization scheduler
still scans `FINALIZING` rooms every 10 seconds after a short ready delay so
failed immediate attempts, lock-busy rooms, server restarts, and
partial-success states are retried and recovered. Both paths compose one
alpha-preserving `FACE`/`BODY`/`LEGS` PNG per `canvasIndex`.

`nemonic.relay.finalization.overlap-height` defaults to `120` px to match the
frontend hint area. If this setting is `0`, the backend keeps the previous
vertical composition. If the setting is positive, the backend overlaps
`FACE`/`BODY` and `BODY`/`LEGS` by the configured pixel height, clamps excessive
overlap so the result remains valid, and renders the overlap with layer priority
`FACE > BODY > LEGS`. This changes only final result rendering; the game still
progresses in `FACE -> BODY -> LEGS` order.

Hint images are not directly composed into the final result. The frontend must
submit drawing images that include the same overlap hint area as the backend
`overlap-height`; if the frontend and backend values diverge, the final image
may look misaligned.

Final result composition uses ARGB images and does not paint a white
background. Transparent PNG input alpha is preserved in both the final original
and resized thumbnail PNGs. Empty auto-submitted or missing parts produce
transparent blank regions instead of white regions.

Finalization uploads final original and thumbnail files under:

```text
relay/results/{artifactId}/original.png
relay/results/{artifactId}/thumbnail.png
```

When the composed image already fits within the thumbnail maximum size, the
thumbnail uses the same PNG bytes as the original to avoid a second PNG encode.
If resizing is required, the thumbnail is still encoded separately with the
existing max-size policy while preserving alpha.

Then persist matching PostgreSQL rows:

- `artifact`
- `relay_drawing_artifact`
- `gallery`

Only non-dropped participants receive gallery rows. Finalization reuses existing
result rows when they already match the expected canvas indexes.

Each finalization run first acquires a room-scoped Redis lock:

```text
relay:room-finalization-lock:{roomCode}
```

The lock is token-scoped and has a 120-second default TTL. After the lock is
acquired, the backend creates a finalization attempt id and stores the attempt
marker for 24 hours by default:

```text
relay:room-finalization-attempt:{roomCode}:{attemptId}
```

The attempt marker records result object keys uploaded by that attempt. This
lets failure logs and orphan cleanup distinguish objects created by the current
attempt from already persisted artifacts.

If a finalization attempt uploads new `relay/results/{artifactId}/...` objects
but fails before the matching PostgreSQL rows are saved, the backend
best-effort deletes only those objects created by the current attempt. Cleanup
failure is logged and does not replace the original finalization error. Existing
artifact rows or reused result objects are never deleted by this rollback path.

If PostgreSQL rows were saved but Redis failed to transition the room from
`FINALIZING` to `FINISHED`, the next retry reads the existing
`artifact.source_room_id = roomCode` results, skips new MinIO uploads and DB
inserts, and only retries the Redis `FINISHED` transition. This recovery emits a
`relay_finalization_recovered` log instead of another `relay_result_created`
business log.

After a room becomes `CLOSED`, cleanup deletes only temporary objects under
`relay/tmp/{roomCode}/`. A fallback cleanup may delete old objects under
`relay/tmp/`, but must not delete active-room temporary files.

A separate orphan object cleanup job may scan old objects under `relay/tmp/` and
`relay/results/`. Temp objects are deleted only when their room state is missing
or already `FINISHED`/`CLOSED`. Result objects are deleted only when they are not
referenced by `artifact.thumbnail_url` or
`relay_drawing_artifact.combined_preview_url` and are not listed in an active
finalization attempt marker. If reference status cannot be determined, the
object is skipped.
Within one cleanup run, temp room-state lookups are cached by `roomCode`, DB
result object references are checked in batches, and active finalization attempt
object references are checked in batches so old object scans do not fan out into
one DB/Redis lookup per object.

## Consequences

- Positive: Final result files survive room closure and temporary cleanup.
- Positive: Result/gallery APIs can serve durable URLs without referencing
  temporary part files.
- Positive: In-game hint images can be rendered by the browser without adding
  MinIO calls to assignment lookup.
- Positive: Empty auto-submitted parts compose as blank areas without requiring
  placeholder uploads.
- Positive: Blank final-result regions remain transparent instead of receiving
  a backend-added white background.
- Positive: Normal final result creation can start immediately after
  `ALL_PARTS_COMPLETED` instead of waiting for the next scheduler tick.
- Positive: The scheduler ready delay remains available for retry, server
  restart, lock-busy, and partial-success recovery.
- Positive: Token-scoped finalization locks make expired-worker cleanup safe in
  repeated scheduler scans.
- Positive: Attempt ids make finalization retry, cleanup, and warning logs
  traceable across one scheduler run.
- Positive: Existing DB results can recover a partially finalized Redis room
  without creating duplicate artifacts.
- Positive: DB-save failures after result upload now try to remove current
  attempt result objects, reducing orphan `relay/results/**` files without
  deleting persisted artifacts.
- Positive: Old temp/result orphan cleanup can reduce storage drift while
  protecting active room temp files and DB-referenced result files.
- Positive: Small final images avoid duplicate PNG encoding when the original
  already satisfies the thumbnail size limit.
- Positive: Orphan cleanup keeps the same conservative deletion rules while
  reducing repeated DB/Redis reference checks during a cleanup run.
- Negative: Hint image rendering depends on `public-url` and bucket read access
  being configured correctly for the client environment.
- Negative: Temporary hint object URLs expose relay temporary object paths while
  the room is active; switch to presigned or proxied URLs if object keys must be
  treated as private.
- Negative: Finalization must coordinate MinIO upload, DB writes, and Redis
  state transition carefully.
- Negative: Orphan cleanup is conservative and best-effort; ambiguous result
  objects are skipped instead of deleted.
- Follow-up: Presigned result URL issuance or CDN URL rewriting can be added
  later without changing the artifact ownership model.
