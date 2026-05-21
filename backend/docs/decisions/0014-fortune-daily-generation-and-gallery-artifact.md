# 0014. Fortune Daily Generation And Gallery Artifact

Date: 2026-05-13

## Status

Accepted

## Context

Fortune was added as a content booth that creates one daily result for an
anonymous user, stores a rendered card image, exposes a requery API, and adds
the result to gallery. Son Da-hyun's implementation history from 2026-05-08
introduced the daily-limit schema, GMS integration, create/requery APIs, card
image storage, nullable design metadata, and KST test stabilization.

The product wording can be interpreted as "one printed fortune per day", but
the current backend implementation consumes the daily generation opportunity
when the server successfully creates and persists the fortune artifact.

## Decision

Use KST as the only date boundary for daily fortune policy. Availability,
creation, and requery all compute "today" with `Asia/Seoul`, and the next
available time is the next KST midnight.

Enforce one fortune per user per KST date with PostgreSQL:

```text
fortune_artifact(user_id, fortune_date)
```

has a unique index. The service checks for an existing row before calling GMS,
and the repository converts unique-index races into the same already-created
conflict response. This avoids extra GMS calls for normal duplicate requests
and still protects concurrent requests.

Expose three public fortune operations:

- `GET /fortune/today/availability`
- `GET /fortune/today`
- `POST /fortune`

All three use `Anonymous-User-UUID` to resolve the anonymous user. Requery does
not call GMS; it restores the response from the saved description JSON for the
current KST date.

Treat front-computed saju data as the create input. Required fields are:

- `calendarType`
- `yearPillar`
- `monthPillar`
- `dayPillar`
- `dayMasterElement`
- `dayBranchElement`
- `dayMasterYinYang`
- `dayBranchYinYang`

`hourPillar` is optional so users who do not know their birth hour can still
generate a fortune.

Generate fortune text through the GMS client. The prompt template comes from
the latest active GMS prompt with feature type `fortune`; if none exists, use
the built-in fallback template. The HTTP client uses the OpenAI-compatible GMS
chat-completions endpoint configured by `nemonic.fortune.gms.*`. Try at most
three attempts. Missing required fields, out-of-range scores, invalid optional
hex colors, malformed JSON, and GMS transport errors all fail the create
request with the service-unavailable fortune message after retries.

Validate and persist the normalized result shape:

- required text: `title`, `summary`, `luckyColor`, `luckyKeyword`,
  `postitLine`
- required scores: `overallLuck`, `loveLuck`, `workLuck`, `moneyLuck`
  in the `0..100` range
- nullable fields: `caution`, `cardTheme`, `bgColor`, `accentColor`, `iconKey`

Render a 900 x 1200 PNG fortune card and upload it under:

```text
fortune/cards/{yyyy}/{MM}/{dd}/{fortuneId}/card.png
```

Then persist one durable artifact set:

- `artifact` with `kind=fortune`, `source_room_id=NULL`, and
  `thumbnail_url` set to the card image object key
- `fortune_artifact` with `description` JSONB, `fortune_image_url`, `user_id`,
  and `fortune_date`
- `gallery` for the creator

Store both a flat response-compatible payload and a nested `saju` copy inside
`fortune_artifact.description`. This lets old flat descriptions and the newer
nested input shape be read by the same requery code.

The daily quota is consumed after server-side fortune creation, card upload,
and DB persistence succeed. Physical print success is not represented in the
current schema or service flow.

## Consequences

- Positive: A generated fortune can be reopened from the database without
  another GMS request.
- Positive: The unique index gives a clear, race-safe once-per-day boundary.
- Positive: Fortune results are normal gallery artifacts and can share gallery
  listing/view behavior with other content.
- Positive: Nullable birth-hour and design fields keep the API usable even when
  the frontend or GMS prompt has partial optional metadata.
- Negative: The card image is uploaded before database rows are saved. If DB
  persistence fails after upload, the MinIO object can remain orphaned.
- Negative: The implemented daily limit is based on successful server artifact
  creation, not printer output success.
- Negative: `fortuneDailyLimit` system parameter data does not currently drive
  fortune creation; the code enforces one per KST date through the unique index.
- Follow-up: Add cleanup for failed create attempts that uploaded
  `fortune/cards/**` before a DB failure.
- Follow-up: Add an explicit print/output state if the product wants physical
  print success, rather than server generation, to consume the daily allowance.
