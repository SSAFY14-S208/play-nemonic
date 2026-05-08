# 0011. Relay Drawing Result Query And Ownership

Date: 2026-05-06

## Status

Accepted

## Context

The frontend result reveal screen needs final combined images plus author
metadata for each `FACE`, `BODY`, and `LEGS` part. Redis room state can expire
after a room is closed, and temporary part images are deleted by cleanup.

The gallery also needs an ownership boundary so dropped or unauthorized users
cannot fetch results they did not receive.

## Decision

Use PostgreSQL as the source of truth for relay result lookup.

The result lookup API reads:

- `artifact`
- `relay_drawing_artifact`
- active `gallery`

The requesting user must own at least one active gallery row for the requested
room result. Soft-deleted gallery rows are excluded.

Redis room state is optional metadata for `roomStatus`. If Redis has expired but
the database result and active gallery ownership exist, result lookup still
succeeds.

Store final reveal metadata in `artifact.meta` when final results are created:

```json
{
  "canvasIndex": 0,
  "roomCode": "AB3K9Q",
  "parts": [
    {
      "part": "FACE",
      "drawerUserUuid": "11111111-1111-1111-1111-111111111111",
      "drawerNickname": "망고"
    }
  ]
}
```

The API parses `artifact.meta` and returns typed DTO fields. It does not expose
the raw metadata string.

Do not return temporary part image URLs. The client should use the final
combined `contentUrl` and crop the image by part for reveal animations.

Result-not-ready polling returns `ready=false` only while Redis room state still
exists and the requester is an eligible non-dropped participant. If durable
results exist but the requester has no active gallery row, return forbidden.
If neither Redis state nor durable result rows exist, return not found.

## Consequences

- Positive: Result lookup works after Redis room state expires.
- Positive: Authorization follows the same gallery ownership model used by
  other content.
- Positive: Temporary part files can be deleted without breaking result reveal.
- Negative: `artifact.meta` is a JSON contract and must remain backward
  compatible when new reveal metadata is added.
- Follow-up: If result metadata grows beyond lightweight reveal fields, add a
  dedicated relational table or JSON schema version.
