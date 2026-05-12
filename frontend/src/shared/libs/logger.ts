// 로거 코어 — 모듈 싱글턴
// React 바깥(global error handler, sendBeacon, ky hook)에서도 동작해야 하므로
// Zustand store가 아닌 모듈 스코프 싱글턴으로 구현한다.

import { runtime } from '@/shared/config'
import { useLogFlowStore } from '@/shared/stores/logFlowStore'
import { useLogSessionStore } from '@/shared/stores/logSessionStore'
import { useUserStore } from '@/shared/stores/userStore'
import type {
  LogContentType,
  LogErrorInfo,
  LogEvent,
  LogEventName,
  LogLevel,
  SamplingTier,
} from '@/shared/types'

import { sanitizeError, sanitizePath, sanitizeReferrer, sanitizeUtmTerm } from './logSanitizer'
import {
  enqueueEvent,
  flushBuffer,
  flushWithBeacon,
  startPeriodicFlush,
  stopPeriodicFlush,
} from './logTransport'

// ── 초기화 상태 ──

let initialized = false

// ── KST 타임스탬프 ──

function toKstIsoString(): string {
  const now = new Date()
  const kstOffset = 9 * 60 * 60 * 1000
  const kst = new Date(now.getTime() + kstOffset)
  // YYYY-MM-DDTHH:mm:ss.SSS+09:00 형식
  const iso = kst.toISOString().replace('Z', '+09:00')
  return iso
}

// ── Sampling ──

const SAMPLING_CONFIG: Record<SamplingTier, Set<LogEventName>> = {
  full: new Set<LogEventName>([
    // 유입 (100%)
    'landing_source_detected',
    'campaign_attributed',
    'share_link_opened',
    'install_prompt_shown',
    'install_prompt_accepted',
    'install_prompt_dismissed',
    // 세션/페이지 (100%)
    'session_start',
    'session_end',
    'page_view',
    'page_leave',
    'client_alive',
    // Funnel (100%)
    'funnel_started',
    'funnel_step_viewed',
    'funnel_step_completed',
    'funnel_goal_reached',
    'funnel_abandoned',
    'cta_clicked',
    // 이탈 (100%)
    'page_exit_intent_detected',
    'room_lobby_abandoned',
    'creation_abandoned',
    'result_share_abandoned',
    // 성능/오류 (100%)
    'js_error',
    'unhandled_rejection',
    'client_network_failed',
    'resource_load_slow',
  ]),
  low: new Set<LogEventName>([
    // 10% — session hash 기반
    'web_vitals',
    'visibility_change',
  ]),
  ui: new Set<LogEventName>([
    // 10~30% — 기능별 조정 가능
    'scroll_depth_reached',
    'modal_opened',
    'modal_closed',
    'tool_selected',
    'canvas_interaction_started',
    'canvas_interaction_paused',
  ]),
}

function getSamplingTier(eventName: LogEventName): SamplingTier {
  if (SAMPLING_CONFIG.full.has(eventName)) return 'full'
  if (SAMPLING_CONFIG.low.has(eventName)) return 'low'
  return 'ui'
}

// session hash 기반 샘플링 — 세션 내 일관성 유지
function shouldSample(eventName: LogEventName): boolean {
  const tier = getSamplingTier(eventName)
  if (tier === 'full') return true

  const sessionId = useLogSessionStore.getState().sessionId
  if (!sessionId) return true // 세션 미생성 시 수집

  // 세션 ID의 마지막 2자리를 해시로 사용 (0-255 범위)
  const hashChar = sessionId.slice(-2)
  const hashValue = parseInt(hashChar, 16)
  if (Number.isNaN(hashValue)) return true

  const rate = tier === 'low' ? 0.1 : 0.2 // low=10%, ui=20%
  return hashValue / 255 < rate
}

// ── Dedup ──

const DEDUP_WINDOW_MS = 200
let lastEventKey = ''
let lastEventTime = 0

function isDuplicate(eventName: LogEventName, metadata?: Record<string, unknown>): boolean {
  const now = Date.now()
  // 에러 이벤트는 같은 메시지일 때만 dedup
  const key =
    eventName +
    (metadata ? JSON.stringify(metadata).slice(0, 200) : '')
  if (key === lastEventKey && now - lastEventTime < DEDUP_WINDOW_MS) {
    return true
  }
  lastEventKey = key
  lastEventTime = now
  return false
}

// ── 공통 metadata 수집 ──

function detectPlatform(): 'desktop' | 'mobile' | 'tablet' {
  if (typeof navigator === 'undefined') return 'desktop'
  const userAgent = navigator.userAgent
  if (/tablet|ipad/i.test(userAgent)) return 'tablet'
  if (/mobile|android|iphone/i.test(userAgent)) return 'mobile'
  return 'desktop'
}

function detectNetwork(): string {
  if (typeof navigator === 'undefined') return 'unknown'
  const connection = (navigator as Navigator & { connection?: { effectiveType?: string } })
    .connection
  return connection?.effectiveType ?? 'unknown'
}

function getCommonMetadata(): Record<string, unknown> {
  if (typeof window === 'undefined') return {}
  return {
    viewport: {
      width: window.innerWidth,
      height: window.innerHeight,
    },
    platform: detectPlatform(),
    locale: navigator.language ?? 'unknown',
    network: detectNetwork(),
  }
}

// ── 메인 진입점 ──

export interface LogEventOptions {
  level?: LogLevel
  contentType?: LogContentType
  roomId?: string
  path?: string
  prevPath?: string
  referrer?: string
  error?: { type?: string; message?: string; stack?: string }
  metadata?: Record<string, unknown>
}

export function logEvent(eventName: LogEventName, options: LogEventOptions = {}): void {
  if (!initialized) return

  // sampling 체크
  if (!shouldSample(eventName)) return

  // dedup 체크
  if (isDuplicate(eventName, options.metadata)) return

  // session idle 회전
  const sessionStore = useLogSessionStore.getState()
  sessionStore.rotateIfIdle()

  // PII sanitize
  const sanitizedPath = options.path ? sanitizePath(options.path) : undefined
  const sanitizedPrevPath = options.prevPath ? sanitizePath(options.prevPath) : undefined
  const sanitizedReferrer = options.referrer
    ? sanitizeReferrer(options.referrer)
    : undefined
  const sanitizedError: LogErrorInfo | undefined = options.error
    ? sanitizeError(options.error)
    : undefined

  // UTM term sanitize (metadata 내)
  let sanitizedMetadata = options.metadata
    ? { ...options.metadata }
    : undefined
  if (sanitizedMetadata?.utm_term && typeof sanitizedMetadata.utm_term === 'string') {
    sanitizedMetadata = {
      ...sanitizedMetadata,
      utm_term: sanitizeUtmTerm(sanitizedMetadata.utm_term),
    }
  }

  // 표준 envelope 빌드
  const event: LogEvent = {
    '@timestamp': toKstIsoString(),
    level: options.level ?? 'INFO',
    service: 'client-web',
    event_name: eventName,
    uuid: useUserStore.getState().userUuid,
    session_id: sessionStore.getOrCreateSessionId(),
    flow_id: useLogFlowStore.getState().flowId ?? undefined,
    content_type: options.contentType,
    room_id: options.roomId,
    path: sanitizedPath,
    prev_path: sanitizedPrevPath,
    referrer: sanitizedReferrer,
    error: sanitizedError,
    metadata: {
      ...getCommonMetadata(),
      ...sanitizedMetadata,
    },
  }

  enqueueEvent(event)
}

// ── 초기화 / 정리 ──

export function initLogger(): void {
  if (initialized) return
  if (!runtime.loggingEnabled) return

  initialized = true
  startPeriodicFlush()
}

export function destroyLogger(): void {
  if (!initialized) return
  stopPeriodicFlush()
  flushBuffer()
  initialized = false
}

// ── 세션 터치 (heartbeat에서 호출) ──

export function touchSession(): void {
  useLogSessionStore.getState().touchActivity()
}

// ── Funnel 헬퍼 ──

export function startFunnel(
  funnelName: string,
  metadata?: Record<string, unknown>,
): string {
  // 진행 중인 funnel이 있으면 abandon 처리
  const currentFlow = useLogFlowStore.getState()
  if (currentFlow.flowId && currentFlow.funnelName) {
    logEvent('funnel_abandoned', {
      metadata: {
        funnel_name: currentFlow.funnelName,
        last_step_name: 'unknown',
        reason: 'route_change',
      },
    })
    currentFlow.endFlow()
  }

  const flowId = useLogFlowStore.getState().startFlow(funnelName)
  logEvent('funnel_started', {
    metadata: {
      funnel_name: funnelName,
      ...metadata,
    },
  })
  return flowId
}

export function logFunnelStep(
  stepName: string,
  stepIndex: number,
  metadata?: Record<string, unknown>,
): void {
  const { funnelName } = useLogFlowStore.getState()
  if (!funnelName) return
  logEvent('funnel_step_viewed', {
    metadata: {
      funnel_name: funnelName,
      step_name: stepName,
      step_index: stepIndex,
      ...metadata,
    },
  })
}

export function completeFunnelStep(
  stepName: string,
  stepIndex: number,
  metadata?: Record<string, unknown>,
): void {
  const { funnelName } = useLogFlowStore.getState()
  if (!funnelName) return
  logEvent('funnel_step_completed', {
    metadata: {
      funnel_name: funnelName,
      step_name: stepName,
      step_index: stepIndex,
      ...metadata,
    },
  })
}

export function reachFunnelGoal(
  goalName: string,
  metadata?: Record<string, unknown>,
): void {
  const { funnelName } = useLogFlowStore.getState()
  if (!funnelName) return
  logEvent('funnel_goal_reached', {
    metadata: {
      funnel_name: funnelName,
      goal_name: goalName,
      ...metadata,
    },
  })
  useLogFlowStore.getState().endFlow()
}

export function abandonFunnel(
  reason: string,
  metadata?: Record<string, unknown>,
): void {
  const { funnelName } = useLogFlowStore.getState()
  if (!funnelName) return
  logEvent('funnel_abandoned', {
    metadata: {
      funnel_name: funnelName,
      reason,
      ...metadata,
    },
  })
  useLogFlowStore.getState().endFlow()
}

// ── 외부에서 사용할 flush 함수 re-export ──

export { flushWithBeacon }
