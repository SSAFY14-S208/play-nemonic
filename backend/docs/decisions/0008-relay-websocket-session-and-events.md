# 0008. Relay Drawing WebSocket Session And Events

Date: 2026-05-06

## Status

Accepted

## Context

Relay drawing needs realtime room updates for lobby membership, connection
state, part progress, automatic submissions, finalization, and room closure.
The backend also needs to handle reconnects and duplicate connections for the
same anonymous user UUID.

## Decision

Use one STOMP endpoint for relay drawing:

```text
/ws/relay
```

Clients identify the session with CONNECT headers:

- `roomCode`
- `Anonymous-User-UUID`

Broadcast room events to:

```text
/topic/relay/rooms/{roomCode}
```

Send personal relay messages to:

```text
/user/queue/relay/rooms/{roomCode}
```

Manage active WebSocket sessions in the common same-server
`WebSocketSessionRegistry`. The session identity is
`connectionType + roomCode + userUuid`; relay drawing uses the relay connection
type for all lookups and closes so it does not close another content type's
session by room code or user UUID. When a duplicate same-server relay session
connects, close the previous relay session and let the new session become the
active one.

Treat Redis `participant.connected` as approved WebSocket connectivity, not as
REST room membership. Room creation, room join, and REST reconnect keep
participants registered with `connected=false`; a successful `/ws/relay` STOMP
CONNECT is the only path that sets the participant to `connected=true`.
DISCONNECT sets it back to `connected=false` and records `disconnectedAt` for
the reconnect flow. The 10-second reconnect grace limit applies only after the
game enters `PLAYING`; in `WAITING`, disconnected registered participants may
REST re-enter and WebSocket reconnect without a grace-time cutoff unless they
were kicked.

The game start command requires every participant to have `connected=true`.
Publish relay room events only after the corresponding Redis CAS save succeeds.
Personal kick messages and same-server session closes are best-effort.

When a relay room becomes closed, publish `ROOM_CLOSED` and best-effort close
all same-server active relay WebSocket sessions for that room. This applies to
host-triggered close, automatic close, last-user waiting-room leave, abandoned
`WAITING`/`PLAYING` cleanup, and finalization-failure cleanup.

When the current part deadline expires, publish a one-time `PART_TIME_UP` room
event during the submission grace window. The event payload includes the timed
out `part`, `partDeadlineAt`, `submitGraceDeadlineAt`,
`autoSubmitGraceMillis`, `pendingCount`, and `pendingSubmissions`. Each pending
submission entry includes `canvasIndex`, `userUuid`, `nickname`, and
`connected`. The event is a client export/submission cue; the backend remains
the source of truth for fallback auto-submit after the grace window.

## Consequences

- Positive: REST room state and WebSocket room state share the same Redis
  source of truth.
- Positive: Clients can distinguish registered lobby participants from
  participants whose WebSocket session is actually connected.
- Positive: Clients can listen to one room topic and optional personal queue.
- Positive: Relay-specific session lookup prevents relay close, kick, and leave
  flows from closing another content type's active session.
- Positive: `PART_TIME_UP` gives clients a server-timed deadline signal and the
  current pending submission list without requiring polling.
- Positive: Event emission does not announce state transitions that failed CAS.
- Negative: The current session registry is in-memory and same-server only.
- Follow-up: Multi-instance deployment needs a shared session coordination
  strategy or sticky sessions before relying on duplicate-session close across
  nodes.
