'use client'

import { useCallback, useMemo, useState } from 'react'

import type { AdminLogsFilter, AdminLogsTimeRange } from '@/shared/types'

import { TIME_RANGE_PRESETS } from '..'
import type { AnalyticsFiltersState, LogsTimeRangePresetKey } from '../types'

// 필터 바 상태 + 백엔드 호출용 timeRange / service filters 파생.
//
// preset === 'custom'이면 사용자가 입력한 customFrom/customTo 그대로 사용.
// 그 외 preset은 매 호출마다 "now - durationMs ~ now"로 평가 → KPI 호출 시점의 시간을
// 반영. refreshNonce를 의존성으로 받는 훅이 같이 자동 갱신.
//
// `refresh()`는 nonce를 +1 → KPI 훅이 재호출. 자동 갱신 토글이 ON이고 페이지가 visible
// 일 때 30초마다 호출됨 (useAnalyticsAutoRefresh).

const DEFAULT_PRESET: LogsTimeRangePresetKey = 'last-24h'
const DEFAULT_CUSTOM_DURATION_MS = 24 * 60 * 60 * 1000

const isoString = (date: Date) => date.toISOString().replace(/\.\d{3}Z$/, 'Z')

// <input type="datetime-local">는 'YYYY-MM-DDTHH:mm'(로컬 시간, 타임존·초 없음)로 값을 돌려준다.
// 백엔드 LogsQueryBuilder는 ISO-8601 UTC(`...Z`)만 받으므로 로컬→UTC 변환이 필요하다.
// new Date(input)이 로컬 시간으로 해석 → toISOString()이 UTC로 직렬화.
const localInputToIso = (localInput: string): string | null => {
  if (!localInput) return null
  const date = new Date(localInput)
  if (Number.isNaN(date.getTime())) return null
  return isoString(date)
}

// ISO Z 문자열 → datetime-local 입력값(YYYY-MM-DDTHH:mm, 로컬). 커스텀 진입 시 초기값 채울 용도.
const isoToLocalInput = (iso: string): string => {
  const date = new Date(iso)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const computeFallbackRange = (): AdminLogsTimeRange => {
  const now = new Date()
  const from = new Date(now.getTime() - DEFAULT_CUSTOM_DURATION_MS)
  return { from: isoString(from), to: isoString(now) }
}

const computeTimeRange = (state: AnalyticsFiltersState): AdminLogsTimeRange => {
  if (state.preset === 'custom') {
    // 사용자가 둘 다 채우기 전(또는 잘못된 값)에는 백엔드에 빈 문자열을 보내지 않고
    // 최근 24h fallback을 사용해 400을 막는다.
    const from = localInputToIso(state.customFrom)
    const to = localInputToIso(state.customTo)
    if (from && to) return { from, to }
    return computeFallbackRange()
  }
  const preset = TIME_RANGE_PRESETS.find((option) => option.key === state.preset)
  const durationMs = preset?.durationMs ?? null
  const now = new Date()
  const from =
    durationMs !== null ? new Date(now.getTime() - durationMs) : new Date(now.getTime() - 60_000)
  return { from: isoString(from), to: isoString(now) }
}

const computeServiceFilters = (services: string[]): AdminLogsFilter[] => {
  // 빈 배열이면 service 필터 없음 — 전체 service.
  // 1개면 단일 term, 여러 개는 negate=false 다중 entries로 OR 처리.
  // 백엔드 LogsFilter는 단일 term per entry라 service:A OR service:B는 multiple entries
  // 로 안 됨 → query string으로 묶어 처리 (호출 측에서 query field에 OR 구문 추가).
  if (services.length === 0) return []
  if (services.length === 1) {
    return [{ field: 'service', value: services[0], negate: false }]
  }
  return []
}

const computeServiceQuery = (services: string[]): string | undefined => {
  if (services.length < 2) return undefined
  return `service:(${services.join(' OR ')})`
}

export function useAnalyticsFilters() {
  const [state, setState] = useState<AnalyticsFiltersState>(() => ({
    preset: DEFAULT_PRESET,
    customFrom: '',
    customTo: '',
    services: ['client-web'],
    autoRefresh: true,
    refreshNonce: 0,
  }))

  const setPreset = useCallback((preset: LogsTimeRangePresetKey) => {
    setState((previous) => {
      // 커스텀 진입 시 input이 비어있으면 현재 활성 프리셋(or 24h)의 범위로 채워
      // 사용자가 빈 input으로 400을 보지 않도록 시작점을 제공한다.
      if (preset === 'custom' && (!previous.customFrom || !previous.customTo)) {
        const previousPreset = TIME_RANGE_PRESETS.find((option) => option.key === previous.preset)
        const durationMs = previousPreset?.durationMs ?? DEFAULT_CUSTOM_DURATION_MS
        const now = new Date()
        const from = new Date(now.getTime() - durationMs)
        return {
          ...previous,
          preset,
          customFrom: isoToLocalInput(from.toISOString()),
          customTo: isoToLocalInput(now.toISOString()),
        }
      }
      return { ...previous, preset }
    })
  }, [])

  const setCustomRange = useCallback((customFrom: string, customTo: string) => {
    setState((previous) => ({ ...previous, customFrom, customTo, preset: 'custom' }))
  }, [])

  const toggleService = useCallback((service: string) => {
    setState((previous) => {
      const exists = previous.services.includes(service)
      const services = exists
        ? previous.services.filter((existing) => existing !== service)
        : [...previous.services, service]
      return { ...previous, services }
    })
  }, [])

  const setAutoRefresh = useCallback((autoRefresh: boolean) => {
    setState((previous) => ({ ...previous, autoRefresh }))
  }, [])

  const refresh = useCallback(() => {
    setState((previous) => ({ ...previous, refreshNonce: previous.refreshNonce + 1 }))
  }, [])

  // KPI 훅에 넘길 trigger 페이로드 — refreshNonce는 의존성으로만 작동하므로
  // 명시적으로 노출. timeRange/serviceFilters/serviceQuery는 useMemo로 안정화해
  // 매 렌더마다 새 객체가 만들어지는 걸 방지.
  const timeRange = useMemo<AdminLogsTimeRange>(
    () => computeTimeRange(state),
    // refresh 시 timeRange도 재평가되도록 nonce 의존.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [state.preset, state.customFrom, state.customTo, state.refreshNonce],
  )
  const serviceFilters = useMemo<AdminLogsFilter[]>(
    () => computeServiceFilters(state.services),
    [state.services],
  )
  const serviceQuery = useMemo<string | undefined>(
    () => computeServiceQuery(state.services),
    [state.services],
  )

  return {
    state,
    timeRange,
    serviceFilters,
    serviceQuery,
    setPreset,
    setCustomRange,
    toggleService,
    setAutoRefresh,
    refresh,
  }
}

export type UseAnalyticsFiltersReturn = ReturnType<typeof useAnalyticsFilters>
