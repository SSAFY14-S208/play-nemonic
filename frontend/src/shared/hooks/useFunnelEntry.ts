'use client'

import { useEffect, useRef } from 'react'

import { startFunnel } from '@/shared/libs'

// Funnel 진입 시 호출하는 thin wrapper hook. 1회만 startFunnel을 발사하고,
// 필수 metadata인 entry_path/entry_type을 자동으로 채운다.
//
// `enabled` (default true) — false이면 발사를 보류한다. flipbook처럼 한 컴포넌트가
// 여러 라우트에 마운트되는 경우, 진입 step일 때만 true로 두어 다른 step에서의
// 재마운트가 funnel을 다시 시작하지 않도록 한다. true로 전환되는 시점에 1회 발사.
//
// startFunnel 자체가 진행 중 funnel을 자동으로 funnel_abandoned 처리하므로 여기서는
// 이전 funnel 정리를 신경쓰지 않는다.
export function useFunnelEntry(
  funnelName: string,
  options?: {
    enabled?: boolean
    extraMetadata?: Record<string, unknown>
  },
): void {
  const startedRef = useRef(false)
  const enabled = options?.enabled ?? true

  useEffect(() => {
    if (!enabled) return
    if (startedRef.current) return
    startedRef.current = true
    if (typeof window === 'undefined') return

    startFunnel(funnelName, {
      entry_path: window.location.pathname,
      entry_type: detectEntryType(),
      ...options?.extraMetadata,
    })
    // extraMetadata는 매 렌더 새 객체일 수 있으므로 dep에 넣지 않는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [funnelName, enabled])
}

function detectEntryType(): string {
  if (typeof window === 'undefined') return 'unknown'

  const params = new URLSearchParams(window.location.search)
  const path = window.location.pathname
  const referrer = document.referrer

  if (
    params.get('utm_source') ||
    params.get('utm_medium') ||
    params.get('utm_campaign')
  ) {
    return 'campaign'
  }

  if (path.startsWith('/share/') || path.includes('/invite/')) {
    return 'share'
  }

  if (params.get('qr')) {
    return 'qr'
  }

  if (!referrer) return 'direct'

  try {
    const referrerHost = new URL(referrer).hostname
    const searchEngines = [
      'google.',
      'naver.',
      'daum.',
      'bing.',
      'yahoo.',
      'duckduckgo.',
    ]
    if (searchEngines.some((engine) => referrerHost.includes(engine))) {
      return 'search'
    }
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
    // referrer parse 실패 — fallthrough
  }

  return 'unknown'
}
