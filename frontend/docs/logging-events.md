# Frontend Logging — Event Catalog

Only `event_name` values listed here are accepted by the ingest endpoint. Undefined names are routed to a dead-letter queue.

## Acquisition Events (100% sampled)

| event_name | When | Required metadata |
|------------|------|-------------------|
| `landing_source_detected` | First page load | `path`, `referrer`, `entry_type`, `utm_*` |
| `campaign_attributed` | UTM or campaign key confirmed | `utm_source`, `utm_medium`, `utm_campaign` |
| `share_link_opened` | Entry via share/QR link | `share_type`, `source_content_type`, `source_id?` |
| `install_prompt_shown` | PWA install prompt shown | `path` |
| `install_prompt_accepted` | Install prompt accepted | `path` |
| `install_prompt_dismissed` | Install prompt dismissed | `path` |

`entry_type` rules: `utm_*` present → `campaign`; share/invite path → `share`; QR param → `qr`; search-engine referrer → `search`; social referrer → `social`; no referrer → `direct`.

## Session & Page Events

| event_name | When | Required metadata |
|------------|------|-------------------|
| `session_start` | New `session_id` issued | `path`, `referrer`, `entry_type`, `viewport`, `platform` |
| `session_end` | `pagehide` / tab close | `duration_ms`, `last_path` |
| `page_view` | Route change | `path`, `prev_path`, `content_type` |
| `page_leave` | Before route exit | `path`, `time_on_page_ms`, `next_path?` |
| `visibility_change` | `document.visibilitychange` (10% sampled) | `state`, `time_visible_ms`, `path` |
| `client_alive` | Active: every 30 s; hidden: every 5 min | `path`, `content_type`, `time_in_session_ms` |

## Funnel / Conversion Events

All funnel events carry `flow_id`. Issue a new `flow_id` at `funnel_started`; keep it until `funnel_goal_reached` or `funnel_abandoned`.

| event_name | When | Required metadata |
|------------|------|-------------------|
| `funnel_started` | Conversion flow begins | `funnel_name`, `entry_path`, `entry_type` |
| `funnel_step_viewed` | Step screen/modal shown | `funnel_name`, `step_name`, `step_index` |
| `funnel_step_completed` | UI advances to next step | `funnel_name`, `step_name`, `step_index` |
| `funnel_goal_reached` | Key goal screen reached | `funnel_name`, `goal_name` |
| `funnel_abandoned` | Exit before completion | `funnel_name`, `last_step_name`, `reason` |
| `cta_clicked` | Primary CTA clicked | `cta_id`, `path`, `funnel_name?` |

Rules:
- `funnel_started` implies step 0 — do not emit a separate `funnel_step_viewed` for step 1
- `funnel_step_completed` means the UI moved to the next step, not server-side save success
- New funnel started while another is active: close previous with `funnel_abandoned` first

Recommended funnels:

| funnel_name | Key steps | goal_name |
|-------------|-----------|-----------|
| `relay_room_creation` | landing, nickname, settings, lobby, drawing, result | `result_viewed` |
| `flipbook_room_creation` | landing, nickname, settings, lobby, drawing, result | `result_viewed` |
| `community_memo_posting` | canvas_view, editor_open, image_select, preview, posted | `memo_post_screen_reached` |
| `fortune_creation` | landing, birth_info, theme_select, loading, result | `fortune_result_viewed` |
| `gallery_save_share` | result_view, save_click, gallery_view, share_click | `share_clicked` |

`funnel_abandoned` reason values: `route_change`, `tab_close`, `background_timeout`, `back_navigation`, `external_link`, `idle_timeout`, `unknown`.

## Exit Events

| event_name | When | Required metadata |
|------------|------|-------------------|
| `page_exit_intent_detected` | Back/close/external navigation intent detected | `path`, `funnel_name?`, `step_name?` |
| `room_lobby_abandoned` | Left lobby before game start | `content_type`, `room_id?`, `wait_time_ms`, `participant_count?` |
| `creation_abandoned` | Left editor before submission | `content_type`, `funnel_name`, `step_name`, `elapsed_ms` |
| `result_share_abandoned` | Viewed result; no save or share action | `content_type`, `time_on_result_ms` |

## UI Engagement Events (10–30% sampled)

| event_name | When | Required metadata |
|------------|------|-------------------|
| `scroll_depth_reached` | 25 / 50 / 75 / 100% scroll depth | `path`, `depth_percent` |
| `modal_opened` | Modal shown | `modal_id`, `path` |
| `modal_closed` | Modal dismissed | `modal_id`, `path`, `reason` |
| `tool_selected` | Drawing/edit tool selected | `tool_id`, `content_type` |
| `canvas_interaction_started` | User begins canvas manipulation | `content_type`, `tool_id?` |
| `canvas_interaction_paused` | No interaction for a sustained period | `content_type`, `elapsed_ms` |
| `phone_official_store_clicked` | Phone modal external shortcut clicked **(100% sampled)** | `shortcut_key`, `destination` |

## Performance & Error Events

| event_name | Sampling | When | Required metadata |
|------------|----------|------|-------------------|
| `web_vitals` | 10% | LCP/INP/CLS/TTFB measured | `metric_name`, `value`, `path` |
| `resource_load_slow` | 100% | Static resource load is slow | `url`, `duration_ms`, `size_bytes?` |
| `js_error` | 100% | `window.onerror` | `path`, `error.type`, `error.message`, `error.stack?` |
| `unhandled_rejection` | 100% | `window.onunhandledrejection` | `path`, `error.type`, `error.message`, `error.stack?` |
| `client_network_failed` | 100% | Fetch never reached the server | `path`, `request_path`, `error.type` |
