// 클라이언트 로그 도메인 (OpenAPI: tag "client-log")

// ── Ingest API request / response ──

export interface ClientLogIngestRequest {
  events: Record<string, unknown>[]
}

export interface ClientLogIngestResponse {
  acceptedCount: number
  droppedCount: number
  systemRoutedCount: number
}

// ── 로그 레벨 ──

export type LogLevel = 'INFO' | 'WARN' | 'ERROR'

// ── 서비스 식별자 ──

export type LogService = 'client-web' | 'next-ssr' | 'client-electron'

// ── 컨텐트 타입 (현재 화면/기능) ──

export type LogContentType =
  | 'landing'
  | 'hub'
  | 'community'
  | 'relay'
  | 'flipbook'
  | 'canvas'
  | 'fortune'
  | 'gallery'
  | 'backoffice'

// ── 유입 경로 판정 ──

export type LogEntryType =
  | 'direct'
  | 'search'
  | 'social'
  | 'qr'
  | 'share'
  | 'campaign'
  | 'unknown'

// ── 이벤트 이름 (스펙 08B 전체) ──

// 유입 분석 (§3)
type SourceEventName =
  | 'landing_source_detected'
  | 'campaign_attributed'
  | 'share_link_opened'
  | 'install_prompt_shown'
  | 'install_prompt_accepted'
  | 'install_prompt_dismissed'

// 세션/페이지 (§4)
type SessionEventName =
  | 'session_start'
  | 'session_end'
  | 'page_view'
  | 'page_leave'
  | 'visibility_change'
  | 'client_alive'

// Funnel/전환 (§5)
type FunnelEventName =
  | 'funnel_started'
  | 'funnel_step_viewed'
  | 'funnel_step_completed'
  | 'funnel_goal_reached'
  | 'funnel_abandoned'
  | 'cta_clicked'
  | 'result_shared'

// 이탈 (§6)
type ChurnEventName =
  | 'page_exit_intent_detected'
  | 'room_lobby_abandoned'
  | 'creation_abandoned'
  | 'result_share_abandoned'

// UI 참여 (§7)
type UiEventName =
  | 'scroll_depth_reached'
  | 'modal_opened'
  | 'modal_closed'
  | 'tool_selected'
  | 'canvas_interaction_started'
  | 'canvas_interaction_paused'
  | 'phone_official_store_clicked'

// 성능/오류 (§8)
type QualityEventName =
  | 'web_vitals'
  | 'resource_load_slow'
  | 'js_error'
  | 'unhandled_rejection'
  | 'client_network_failed'

export type LogEventName =
  | SourceEventName
  | SessionEventName
  | FunnelEventName
  | ChurnEventName
  | UiEventName
  | QualityEventName

// ── 이탈 사유 ──

export type AbandonReason =
  | 'route_change'
  | 'tab_close'
  | 'background_timeout'
  | 'back_navigation'
  | 'external_link'
  | 'idle_timeout'
  | 'unknown'

// ── 에러 객체 (sanitized) ──

export interface LogErrorInfo {
  type: string
  message: string
  stack?: string
}

// ── 표준 로그 이벤트 envelope ──

export interface LogEvent {
  '@timestamp': string
  level: LogLevel
  service: LogService
  event_name: LogEventName
  uuid: string | null
  session_id: string
  trace_id?: string
  flow_id?: string
  content_type?: LogContentType
  room_id?: string
  path?: string
  prev_path?: string
  referrer?: string
  error?: LogErrorInfo
  metadata?: Record<string, unknown>
}

// ── Sampling 설정 ──

export type SamplingTier = 'full' | 'low' | 'ui'

// ── Funnel 이름 (권장 funnel) ──

export type FunnelName =
  | 'relay_room_creation'
  | 'flipbook_room_creation'
  | 'community_memo_posting'
  | 'fortune_creation'
  | 'gallery_save_share'
  | 'infinite_canvas_creation'
  | (string & {}) // 추후 추가 funnel 허용

// ── sendBeacon 우선순위 ──

export type BeaconPriority = 'error' | 'funnel' | 'ui' | 'heartbeat'
