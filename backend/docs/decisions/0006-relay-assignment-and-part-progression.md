# 0006. Relay Drawing Assignment And Part Progression

Date: 2026-05-06

## Status

Accepted

## Context

Relay drawing must create one final character per participant. Each character is
composed from `FACE`, `BODY`, and `LEGS`. The drawing experience depends on the
initial participant order and on each part moving to the next participant in a
predictable ring.

Participants can leave or be dropped, but reassigning work mid-game would change
the intended author sequence and make client timelines hard to reason about.

## Decision

Generate the full assignment table once when the game starts.

For `N` participants, create `N` canvas indexes. For each canvas index and part:

```text
assigned participant index = (canvasIndex + partIndex) % participantCount
```

`partIndex` is:

- `FACE`: `0`
- `BODY`: `1`
- `LEGS`: `2`

Assignments are not recalculated after game start. `joinOrder` is not
renumbered when participants leave or are kicked from a waiting room. Any
participant who re-enters later receives a new order according to the current
join policy rather than reclaiming a removed list position.

A part is complete when all assignments for that part are either `SUBMITTED` or
`AUTO_SUBMITTED`. Part progression is fixed:

```text
FACE -> BODY -> LEGS -> FINALIZING
```

## Consequences

- Positive: A room with `N` participants produces `N` final result artifacts.
- Positive: The author sequence is deterministic and testable.
- Positive: Game-time disconnect handling can fill dropped assignments without
  redistributing work to remaining participants.
- Negative: Dropped participants may still appear as part authors in final
  metadata because assignment authorship is preserved.
- Follow-up: A future product decision would be required to support dynamic
  reassignment or late game joins.
