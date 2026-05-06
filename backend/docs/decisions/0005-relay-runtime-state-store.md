# 0005. Relay Drawing Runtime State Store

Date: 2026-05-06

## Status

Accepted

## Context

Relay drawing rooms need fast realtime updates for lobby membership,
WebSocket connection status, game progress, assignment submission, timeout,
disconnect grace, and cleanup coordination. These values change frequently and
do not need to become long-lived history until final artifacts are created.

The product also uses the public room code as the invite code, WebSocket room
identifier, and room lookup key.

## Decision

Use `roomCode` as the only in-progress relay room identifier. Do not introduce a
separate internal room id for active relay rooms.

Store active room state as JSON in Redis under:

```text
relay:room:{roomCode}
```

The Redis room state is the source of truth while a room is active. It contains
the room status, host UUID, settings, participants, assignments, current part,
deadlines, timestamps, and kicked user UUIDs. It does not store image bytes.

Use PostgreSQL only for durable outputs after finalization:

- `artifact`
- `relay_drawing_artifact`
- `gallery`

`artifact.source_room_id` stores the relay `roomCode` for final result lookup.

## Consequences

- Positive: Active room updates stay cheap and can use Redis optimistic CAS.
- Positive: Invite links, WebSocket topics, Redis keys, and result source room
  ids all share the same public `roomCode`.
- Positive: No PostgreSQL rows are created for rooms that never produce results.
- Negative: An active room cannot be reconstructed if its Redis state expires
  before finalization.
- Follow-up: Any future long-lived audit or replay feature must add a separate
  persistence model instead of overloading the Redis room state.
