# 0013. Flipbook Room Lifecycle And Result Finalization

Date: 2026-05-13

## Status

Accepted

## Context

Flipbook started as a room-based drawing game like Relay, then gained its own
lobby policy, WebSocket events, runtime system parameters, timeout handling,
disconnect recovery, result finalization, and gallery persistence across
Son Da-hyun's implementation commits from 2026-05-06 through 2026-05-12.

The implementation also has a product policy that is easy to rediscover
incorrectly: the configured frame count is the target based on the participants
who successfully start the game, but frames that are auto-submitted empty
because of timeout or disconnect are excluded from the final GIF. Therefore a
finished flipbook result may have fewer rendered frames than the configured
minimum frame count.

## Decision

Keep the authoritative room runtime state in Redis. A room moves through:

```text
WAITING -> PLAYING -> FINALIZING -> FINISHED -> CLOSED
```

Room state is stored under the flipbook room key, updated through optimistic
CAS (`saveIfUnchanged`), and synchronized back to invite metadata after
successful state changes. PostgreSQL stores only durable artifacts and gallery
ownership, not the live game timeline.

Read these runtime policies from backoffice system parameters with safe
fallbacks:

- `flipbook.room_participant_limit`
- `flipbook.room_time_limit_seconds`
- `flipbook.min_frames_per_flipbook`
- `flipbook.reconnect_grace_seconds`

Room create and room query responses expose the time-limit options so the
frontend can render the same allowed values the backend validates. Room settings
can change only while `WAITING`, only by the host, and only to one of the
allowed time-limit values.

Invite join and WebSocket connection are separated. Invite join adds or returns
a participant record, but the participant is not counted as connected until the
STOMP connection succeeds. New participants may enter only `WAITING` rooms.
Existing participants may return while the room is still reconnectable, but
kicked or dropped participants cannot re-enter. Game start requires the host,
`WAITING` status, enough participants for the configured minimum, and every
starting participant connected through WebSocket.

Flipbook WebSocket connections use `/ws/flipbook`, room broadcasts use
`/topic/flipbook/rooms/{roomCode}`, and session-scoped user queues use
`/user/queue/flipbook/rooms/{roomCode}`. A duplicate same-user session replaces
the previous session. Disconnect processing updates Redis only when the
disconnecting session is the current session registered for that room/user.

At game start, generate the entire assignment table from the connected
participants in join order:

- `N` starting participants produce `N` flipbook indexes.
- `flipbook.min_frames_per_flipbook` resolves the total round count.
- Round `r` assigns flipbook index `i` to participant
  `(i + r - 1) % participantCount`.
- Frame index is `round - 1`.

Frame submission uses the general file upload flow:

```text
presign -> direct PUT to MinIO -> confirm upload -> submit frame
```

The submitted file must belong to the submitting user, must be uploaded, and
must use `FileUploadPurpose.FLIPBOOK`. Redis assignments store the file id and
object key; final GIF generation later downloads the stored object keys. The
presign/confirm flow does not decode the uploaded image bytes or add a white
background.

Timeout processing is backend-authoritative. When the round deadline passes,
the backend publishes one `ROUND_TIME_UP` event during the auto-submit grace
window. If the frame is still pending after `roundDeadlineAt +
auto-submit-grace`, the assignment becomes `AUTO_SUBMITTED`, `empty=true`, and
has no file/object key. User submissions remain accepted only until that same
grace deadline. Submission locks and room mutation locks protect concurrent
manual submissions, timeout auto-submit, and disconnect auto-submit.

Disconnect grace applies only during `PLAYING`. The reconnect grace period is
resolved from the runtime setting on each scheduler pass and reconnect
validation. When a participant is disconnected past the grace window, they are
marked dropped. Dropped participants cannot rejoin or reconnect. Their current
round pending assignments are auto-submitted empty; future assignments are
handled when they become current. If the dropped participant was the host,
ownership transfers to the connected, non-dropped participant with the lowest
join order when such a participant exists.

When all rounds complete, move the room to `FINALIZING`, publish
`ALL_ROUNDS_COMPLETED`, and queue an immediate asynchronous finalization
attempt. The scheduler still scans `FINALIZING` rooms every 10 seconds by
default, after a short ready delay, so server restarts, lock-busy rooms, and
failed immediate attempts are recovered.

Finalization uses a room-scoped Redis lock:

```text
flipbook:room-finalization-lock:{roomCode}
```

Failures increment a Redis retry counter with the same TTL as the room state.
The default maximum retry count is 60. On the final failure, the room is closed
with `close_reason=finalization_failed` and `ROOM_CLOSED` is published.

Final GIF generation includes only assignments that are:

```text
status == SUBMITTED
empty == false
autoSubmitted == false
objectKey is present
```

This encodes the product decision that timeout/disconnect empty frames are not
rendered into the final GIF. If no valid result frame remains at all, do not
create an empty GIF or gallery artifact. Close the room directly with
`close_reason=no_result_frames` and publish `ROOM_CLOSED`.

When at least one valid frame remains, group valid frames by `flipbookIndex`,
sort by `frameIndex`, compose a GIF and thumbnail, and upload them under:

```text
flipbook/results/{artifactId}/result.gif
flipbook/results/{artifactId}/thumbnail.png
```

Result composition must not paint transparent frame backgrounds white. GIF
normalization uses ARGB frames before writing while preserving the existing
`image/gif` contract. The thumbnail PNG resize path also uses ARGB so
transparent PNG frame alpha remains transparent in the thumbnail. GIF remains a
palette-based format, so it does not provide PNG-style full alpha precision;
APNG or animated WebP would be a separate contract-changing follow-up if higher
fidelity transparency becomes necessary.

Then persist:

- `artifact` with `kind=flipbook` and `source_room_id=roomCode`
- `flipbook_artifact`
- `gallery`

Only non-dropped participants receive gallery rows. Result lookup requires an
active gallery row for the requesting user. If artifacts for the room already
exist and their flipbook indexes match the expected submitted indexes, reuse
them and only retry the Redis transition to `FINISHED`.

Finished rooms are automatically closed after
`nemonic.flipbook.close.delay-seconds`, defaulting to 300 seconds. Abandoned
rooms are also cleaned up: empty waiting rooms close immediately, waiting rooms
where all participants remain disconnected past the configured idle duration
close with `close_reason=waiting_idle_timeout`, and playing rooms where every
participant is disconnected or dropped past the configured abandoned duration
close with `close_reason=playing_abandoned`.

`ROOM_CLOSED` includes additive `closeReason` metadata. Current close reasons
include `waiting_empty`, `waiting_idle_timeout`, `playing_abandoned`,
`auto_delay`, `finalization_failed`, and `no_result_frames`.

## Consequences

- Positive: The normal result path starts immediately after the last round
  completes while scheduler recovery remains available.
- Positive: Runtime settings changed from backoffice apply to newly created
  rooms, start policy, reconnect grace, and frontend time-limit options without
  a deploy.
- Positive: Empty timeout/disconnect frames are excluded from final GIF output,
  matching the confirmed product policy that results may contain fewer frames
  than the configured target.
- Positive: Rooms with zero renderable frames no longer produce empty
  artifacts; clients receive a clear `ROOM_CLOSED.closeReason=no_result_frames`.
- Positive: Existing result rows can recover a `FINALIZING` room without
  duplicate GIF uploads when only the Redis `FINISHED` transition failed.
- Positive: Result generation no longer adds a backend white background to
  transparent frames, and PNG thumbnails keep transparent backgrounds.
- Positive: Gallery ownership is explicit and excludes dropped participants.
- Negative: Unlike Relay, flipbook finalization does not currently track an
  attempt marker or delete newly uploaded result objects if DB persistence fails
  after upload.
- Negative: Finalization retry count and scheduler cadence are environment
  properties, not backoffice system parameters.
- Follow-up: Add Relay-style result upload rollback/orphan cleanup for
  `flipbook/results/**` if storage drift becomes operationally visible.
- Follow-up: Consider APNG or animated WebP only if GIF palette transparency is
  not sufficient for product-quality transparent animation.
