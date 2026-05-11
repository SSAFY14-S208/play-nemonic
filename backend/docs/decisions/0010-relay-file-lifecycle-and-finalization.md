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

When all parts are completed, move the room to `FINALIZING`. The finalization
scheduler waits a short ready delay after the `FINALIZING` update before
processing the room, then composes one vertical `FACE`/`BODY`/`LEGS` PNG per
`canvasIndex`, uploads final original and thumbnail files under:

```text
relay/results/{artifactId}/original.png
relay/results/{artifactId}/thumbnail.png
```

Then persist matching PostgreSQL rows:

- `artifact`
- `relay_drawing_artifact`
- `gallery`

Only non-dropped participants receive gallery rows. Finalization reuses existing
result rows when they already match the expected canvas indexes.

Use a token-scoped Redis finalization lock before composing a room. Store the
token as the lock value and release the lock only when the stored token still
matches, so an expired worker cannot release another worker's active lock.

After a room becomes `CLOSED`, cleanup deletes only temporary objects under
`relay/tmp/{roomCode}/`. A fallback cleanup may delete old objects under
`relay/tmp/`, but must not delete `relay/results/**` or database rows.

## Consequences

- Positive: Final result files survive room closure and temporary cleanup.
- Positive: Result/gallery APIs can serve durable URLs without referencing
  temporary part files.
- Positive: In-game hint images can be rendered by the browser without adding
  MinIO calls to assignment lookup.
- Positive: Empty auto-submitted parts compose as blank areas without requiring
  placeholder uploads.
- Positive: The ready delay reduces races between the Redis `FINALIZING`
  transition and result generation.
- Positive: Token-scoped finalization locks make expired-worker cleanup safe in
  repeated scheduler scans.
- Negative: Hint image rendering depends on `public-url` and bucket read access
  being configured correctly for the client environment.
- Negative: Temporary hint object URLs expose relay temporary object paths while
  the room is active; switch to presigned or proxied URLs if object keys must be
  treated as private.
- Negative: Finalization must coordinate MinIO upload, DB writes, and Redis
  state transition carefully.
- Follow-up: Presigned result URL issuance or CDN URL rewriting can be added
  later without changing the artifact ownership model.
