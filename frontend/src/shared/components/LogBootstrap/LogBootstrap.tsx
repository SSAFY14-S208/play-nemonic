'use client'

import { useEffect } from 'react'

import { useClientAlive } from '@/shared/hooks/useClientAlive'
import { usePageTracking } from '@/shared/hooks/usePageTracking'
import { useWebVitals } from '@/shared/hooks/useWebVitals'
import {
  destroyLogger,
  flushWithBeacon,
  initLogger,
  logEvent,
} from '@/shared/libs'

// 로깅 부트스트랩 — 모든 자동 추적 훅을 마운트하고 글로벌 이벤트 핸들러를 등록한다.
export function LogBootstrap() {
  // 자동 추적 훅
  usePageTracking()
  useWebVitals()
  useClientAlive()

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (cancelled) return

      // 로거 초기화
      initLogger()

      // 유입 이벤트
      const entryType = detectEntryType()
      logEvent('landing_source_detected', {
        path: window.location.pathname,
        referrer: document.referrer || undefined,
        metadata: {
          entry_type: entryType,
          ...extractUtmParams(),
        },
      })

      // 세션 시작
      logEvent('session_start', {
        path: window.location.pathname,
        referrer: document.referrer || undefined,
        metadata: {
          entry_type: entryType,
        },
      })

      // 글로벌 에러 핸들러 (bubble phase — MetaMask 가드는 capture phase에서 처리)
      window.addEventListener('error', handleError)
      window.addEventListener('unhandledrejection', handleRejection)
      document.addEventListener('visibilitychange', handleVisibilityChange)
      window.addEventListener('pagehide', handlePageHide)
    })()

    return () => {
      cancelled = true
      window.removeEventListener('error', handleError)
      window.removeEventListener('unhandledrejection', handleRejection)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      window.removeEventListener('pagehide', handlePageHide)
      destroyLogger()
    }
  }, [])

  return null
}

// ── 글로벌 이벤트 핸들러 ──

function handleError(event: ErrorEvent) {
  logEvent('js_error', {
    level: 'ERROR',
    path: window.location.pathname,
    error: {
      type: event.error?.name ?? 'Error',
      message: event.message ?? '',
      stack: event.error?.stack,
    },
  })
}

function handleRejection(event: PromiseRejectionEvent) {
  const reason = event.reason
  logEvent('unhandled_rejection', {
    level: 'ERROR',
    path: window.location.pathname,
    error: {
      type: reason?.name ?? 'UnhandledRejection',
      message: reason?.message ?? String(reason ?? ''),
      stack: reason?.stack,
    },
  })
}

function handleVisibilityChange() {
  if (document.visibilityState === 'hidden') {
    logEvent('session_end', {
      path: window.location.pathname,
      metadata: {
        last_path: window.location.pathname,
      },
    })
    flushWithBeacon()
  }
}

function handlePageHide() {
  flushWithBeacon()
}

// ── 유입 분석 헬퍼 ──

function extractUtmParams(): Record<string, string> {
  if (typeof window === 'undefined') return {}
  const params = new URLSearchParams(window.location.search)
  const utmKeys = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_content', 'utm_term'] as const
  const result: Record<string, string> = {}
  for (const key of utmKeys) {
    const value = params.get(key)
    if (value) result[key] = value
  }
  return result
}

function detectEntryType(): string {
  if (typeof window === 'undefined') return 'unknown'

  const params = new URLSearchParams(window.location.search)
  const path = window.location.pathname
  const referrer = document.referrer

  // UTM 파라미터 존재
  if (params.get('utm_source') || params.get('utm_medium') || params.get('utm_campaign')) {
    return 'campaign'
  }

  // 공유 path 또는 invite/share token
  if (path.startsWith('/share/') || path.includes('/invite/')) {
    return 'share'
  }

  // QR 전용 파라미터
  if (params.get('qr')) {
    return 'qr'
  }

  if (!referrer) return 'direct'

  try {
    const referrerHost = new URL(referrer).hostname
    // 자체 도메인 referrer — 페이지 내 navigation, 새로고침, 새 탭으로 우리 사이트를
    // 다시 연 경우에 자체 호스트가 referrer로 잡힌다. 외부 유입이 아니므로 direct로
    // 분류해 unknown 노이즈를 줄인다.
    if (referrerHost === window.location.hostname) {
      return 'direct'
    }
    // 검색 엔진
    const searchEngines = ['google.', 'naver.', 'daum.', 'bing.', 'yahoo.', 'duckduckgo.']
    if (searchEngines.some((engine) => referrerHost.includes(engine))) {
      return 'search'
    }
    // 소셜 도메인
    const socialDomains = [
      'facebook.',
      'twitter.',
      'x.com',
      'instagram.',
      'linkedin.',
      'kakao.',
      't.co',
    ]
    if (socialDomains.some((domain) => referrerHost.includes(domain))) {
      return 'social'
    }
  } catch {
    // referrer 파싱 실패
  }

  return 'unknown'
}
