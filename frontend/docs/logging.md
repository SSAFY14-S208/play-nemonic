# Frontend Logging

## Purpose & Scope

Frontend logs explain where users came from, which screens they visited, and where they converted or dropped off. Do not duplicate what the backend already logs.

| Category | Frontend collects | Backend collects |
|----------|-------------------|-----------------|
| Acquisition | referrer, UTM, share/QR entry, landing path | Nginx/API access log |
| Conversion | CTA clicks, funnel step entry/completion, result screen reached | API success/failure, domain results |
| Exit | page leave, funnel abandon, visibility hidden | WebSocket disconnect, timeout |
| Performance | Web Vitals, slow static resources | API latency, DB/GMS/MinIO latency |
| Errors | JS error, unhandled rejection, network failure (server unreachable) | 4xx/5xx, validation, auth |

**Never collect:** `api_request`, `api_error`, HTTP status, API latency, response/request body.

## Log Schema

All events share these fields:

| Field | Value |
|-------|-------|
| `@timestamp` | `new Date().toISOString()` normalized to KST (`+09:00`) |
| `level` | `INFO` / `WARN` / `ERROR` — standard events use `INFO` |
| `service` | `client-web` (browser default), `next-ssr`, `client-electron` |
| `trace_id` | UUID per fetch call — rotate per fetch, pass as `X-Trace-Id` header |
| `flow_id` | UUID per funnel run — issued at `funnel_started`, passed as `X-Flow-Id` |
| `session_id` | UUID per browser tab — `sessionStorage`, rotate after 30 min idle |
| `uuid` | Anonymous user UUID — `localStorage` |
| `event_name` | `snake_case` — only names defined in `docs/logging-events.md` |
| `content_type` | `landing` / `hub` / `community` / `relay` / `flipbook` / `canvas` / `fortune` / `gallery` / `backoffice` |
| `room_id` | room/canvas code — only on relevant screens |
| `metadata` | Event-specific fields (see `docs/logging-events.md`) |
| `error` | `{type, message, stack}` — error events only, after PII sanitization |

**`trace_id` vs `flow_id`:**
- `trace_id` — one HTTP/WS transaction; joins with backend access/error log (1:1)
- `flow_id` — one funnel run (spans multiple trace_ids); joins all frontend + backend events for that funnel

Common metadata: `path`, `prev_path`, `referrer`, `utm_*`, `entry_type`, `viewport`, `platform`, `locale`, `network`.

## Transmission

```
POST /api/logs/client
Content-Type: application/json
{ "events": [ { ...log event... } ] }
```

- Buffer: flush at 50 events or every 5 seconds
- Page exit: `visibilitychange === 'hidden'` (primary) + `pagehide` (fallback) → `navigator.sendBeacon`
- `sendBeacon` limit: 64 KB; if exceeded, split by priority: `error_*` > funnel/exit > UI engagement > heartbeat
- Error events (`js_error`, `unhandled_rejection`, `client_network_failed`): flush immediately
- Never recursively log transmission failures
- Dedup repeated identical events within 200 ms (especially `js_error`)

## Sampling

| Event category | Rate | Reason |
|----------------|------|--------|
| Acquisition, session, page, funnel, exit | 100% | Required for conversion analysis |
| `client_alive` | 100% | Real-time active user estimation |
| `js_error`, `unhandled_rejection`, `client_network_failed` | 100% | Incident analysis |
| `phone_official_store_clicked` | 100% | External exit trend |
| `web_vitals` | 10% (session-hash based) | Volume control |
| `visibility_change` | 10% (session-hash based) | Volume control |
| `resource_load_slow` | 100% | Low occurrence rate |
| Detailed UI engagement | 10–30% per feature | Expand when needed |

Use session-hash-based sampling so the full flow of a sampled session is captured unbroken.
Distribution metrics (e.g. `web_vitals` at 10%) are only displayed when sample N ≥ 100 in the evaluation window.

## PII — Never Include

- User-authored content: drawing strokes, memo text, fortune input details
- Auth credentials: JWT, refresh token, OAuth code, password
- Personal data: email, phone, real name, address
- Raw URL tokens

**Allowed:** `uuid`, `session_id`, `flow_id`, `trace_id`, normalized `path`, normalized referrer origin, UTM values (cap `utm_term` at 50 chars + strip known PII patterns).

Path normalization:
```
/share/secret-token       →  /share/:token
/flipbook/rooms/AB3K9Q    →  /flipbook/rooms/:roomCode
/gallery/12345            →  /gallery/:galleryId
```

Error sanitization (apply at logger entry point):
- Mask URL/path token patterns (`/share/{token}`, `?token=…`, `Authorization: …`)
- Length cap: `message` 1 KB, `stack` 4 KB — truncate + `…[truncated]`
- Replace known secret prefixes (`sk-`, `Bearer `, `eyJ…` JWT) with `***`

## Backend Correlation

- `flow_id` — funnel-level join key: all frontend events in a funnel + all backend events from fetches within that funnel share this value
- `trace_id` — request-level join key: one fetch call + the backend logs it generated share this value

Both IDs are sent as request headers (`X-Trace-Id`, `X-Flow-Id`) on every fetch.

## Implementation Checklist

- [ ] `shared/libs/logger.ts` manages `uuid`, `session_id`, `trace_id`, `flow_id`
- [ ] `session_id` is `sessionStorage`-based; rotates after 30 min idle
- [ ] Route change: emit `page_leave` → `page_view` in order; `trace_id` does not rotate on route change
- [ ] First entry: emit `landing_source_detected`, then `session_start`
- [ ] All main flows instrumented: `funnel_started` (issues new `flow_id`), `funnel_step_viewed`, `funnel_step_completed`, `funnel_goal_reached`, `funnel_abandoned`
- [ ] New funnel while another is in progress: close previous with `funnel_abandoned` before issuing new `flow_id`
- [ ] Page exit: `visibilitychange === 'hidden'` (primary) + `pagehide` (fallback) → emit `session_end` + flush via `sendBeacon`
- [ ] `client_alive` heartbeat: 30 s when active, 5 min when hidden
- [ ] Web Vitals: collect LCP/INP/CLS/TTFB via `web-vitals` v3+
- [ ] Fetch wrapper: inject `X-Trace-Id` and `X-Flow-Id` headers only — do not log API success/failure
- [ ] Only emit `client_network_failed` for fetches that never reached the server
- [ ] PII sanitization and error masking applied at logger entry point
