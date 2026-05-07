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
`WebSocketSessionRegistry`. The session identity is `roomCode + userUuid`.
When a duplicate same-server session connects, close the previous session and
let the new session become the active one.

Treat Redis `participant.connected` as approved WebSocket connectivity, not as
REST room membership. Room creation, room join, and REST reconnect keep
participants registered with `connected=false`; a successful `/ws/relay` STOMP
CONNECT is the only path that sets the participant to `connected=true`.
DISCONNECT sets it back to `connected=false` and records `disconnectedAt` for
the reconnect grace flow.

The game start command requires every participant to have `connected=true`.
Publish relay room events only after the corresponding Redis CAS save succeeds.
Personal kick messages and same-server session closes are best-effort.

## Consequences

- Positive: REST room state and WebSocket room state share the same Redis
  source of truth.
- Positive: Clients can distinguish registered lobby participants from
  participants whose WebSocket session is actually connected.
- Positive: Clients can listen to one room topic and optional personal queue.
- Positive: Event emission does not announce state transitions that failed CAS.
- Negative: The current session registry is in-memory and same-server only.
- Follow-up: Multi-instance deployment needs a shared session coordination
  strategy or sticky sessions before relying on duplicate-session close across
  nodes.
