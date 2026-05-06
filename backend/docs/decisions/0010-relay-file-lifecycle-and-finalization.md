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

When all parts are completed, move the room to `FINALIZING`. The finalization
scheduler composes one vertical `FACE`/`BODY`/`LEGS` PNG per `canvasIndex`,
uploads final original and thumbnail files under:

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

After a room becomes `CLOSED`, cleanup deletes only temporary objects under
`relay/tmp/{roomCode}/`. A fallback cleanup may delete old objects under
`relay/tmp/`, but must not delete `relay/results/**` or database rows.

## Consequences

- Positive: Final result files survive room closure and temporary cleanup.
- Positive: Result/gallery APIs can serve durable URLs without referencing
  temporary part files.
- Positive: Empty auto-submitted parts compose as blank areas without requiring
  placeholder uploads.
- Negative: Finalization must coordinate MinIO upload, DB writes, and Redis
  state transition carefully.
- Follow-up: Presigned result URL issuance or CDN URL rewriting can be added
  later without changing the artifact ownership model.
