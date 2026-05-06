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

Update Redis participant connection state on WebSocket connect/disconnect.
Publish relay room events only after the corresponding Redis CAS save succeeds.
Personal kick messages and same-server session closes are best-effort.

## Consequences

- Positive: REST room state and WebSocket room state share the same Redis
  source of truth.
- Positive: Clients can listen to one room topic and optional personal queue.
- Positive: Event emission does not announce state transitions that failed CAS.
- Negative: The current session registry is in-memory and same-server only.
- Follow-up: Multi-instance deployment needs a shared session coordination
  strategy or sticky sessions before relying on duplicate-session close across
  nodes.
