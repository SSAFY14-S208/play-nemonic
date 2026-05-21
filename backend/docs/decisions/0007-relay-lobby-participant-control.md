# 0007. Relay Drawing Lobby Participant Control

Date: 2026-05-06

## Status

Accepted

## Context

Relay drawing has two different lobby removal actions:

- Host kick: a moderation action by the room host.
- Voluntary leave: a participant's own action.

Both actions happen before the game starts. Game-time removal has different
rules because assignments are already fixed.

## Decision

Allow host kick and voluntary leave only while the room is `WAITING`.

Host kick uses:

```http
POST /api/v1/relay/rooms/{roomCode}/participants/kick
```

with `targetUserUuid` in the JSON body. The target UUID is kept out of the URL
because it is command data, not a public resource id for browsing. A kicked user
is removed from `participants` and added to `kickedUserUuids` in Redis. Kicked
users cannot rejoin the same room or reconnect to its WebSocket session. After
the successful Redis state transition, publish `PARTICIPANT_KICKED`, send a
best-effort personal `KICKED_FROM_ROOM` message, and close the kicked user's
same-server active relay WebSocket session with the kick close reason.

Voluntary leave uses:

```http
DELETE /api/v1/relay/rooms/{roomCode}/participants/me
```

Voluntary leave removes the caller from `participants` but does not add the
caller to `kickedUserUuids`, so the user may rejoin while the room remains
joinable.

If the leaving user is the host, transfer host ownership to the remaining
participant with the lowest `joinOrder`. If the last participant leaves, mark
the room `CLOSED`. After the successful Redis state transition, publish
`PARTICIPANT_LEFT` plus any required `HOST_CHANGED` or `ROOM_CLOSED` event and
close the leaving user's same-server active relay WebSocket session.

Do not renumber remaining `joinOrder` values.

## Consequences

- Positive: Kick and leave have distinct product semantics and rejoin behavior.
- Positive: Host transfer is deterministic and follows entrance order.
- Positive: Last-user leave closes the room without introducing a separate room
  deletion API.
- Positive: Waiting-room removal APIs and WebSocket connection lifecycle stay
  synchronized for clients on the same server.
- Negative: `joinOrder` values may have gaps after removals.
- Follow-up: Game-time kick, host delegation UI, and host self-delegation remain
  separate product decisions.
