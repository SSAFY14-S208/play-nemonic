'use client'

import { useCallback, useMemo, useState } from 'react'

import type { AdminMetricsTimeRange } from '@/shared/types'

import { METRICS_TIME_RANGE_PRESETS } from '../constants'
import type { MetricsFiltersState, MetricsTimeRangePresetKey } from '../types'

// system observability 필터 상태. marketing dashboard와 별도 인스턴스 — service
// 멀티 셀렉트 없음, 더 짧은 시간 프리셋 default.

const DEFAULT_PRESET: MetricsTimeRangePresetKey = 'last-1h'

const isoString = (date: Date) => date.toISOString().replace(/\.\d{3}Z$/, 'Z')

const computeTimeRange = (state: MetricsFiltersState): AdminMetricsTimeRange => {
  if (state.preset === 'custom') {
    return { from: state.customFrom, to: state.customTo }
  }
  const preset = METRICS_TIME_RANGE_PRESETS.find((option) => option.key === state.preset)
  const durationMs = preset?.durationMs ?? 60 * 60 * 1000
  const now = new Date()
  const from = new Date(now.getTime() - durationMs)
  return { from: isoString(from), to: isoString(now) }
}

export function useMetricsFilters() {
  const [state, setState] = useState<MetricsFiltersState>(() => ({
    preset: DEFAULT_PRESET,
    customFrom: '',
    customTo: '',
    autoRefresh: true,
    refreshNonce: 0,
  }))

  const setPreset = useCallback((preset: MetricsTimeRangePresetKey) => {
    setState((previous) => ({ ...previous, preset }))
  }, [])

  const setCustomRange = useCallback((customFrom: string, customTo: string) => {
    setState((previous) => ({ ...previous, customFrom, customTo, preset: 'custom' }))
  }, [])

  const setAutoRefresh = useCallback((autoRefresh: boolean) => {
    setState((previous) => ({ ...previous, autoRefresh }))
  }, [])

  const refresh = useCallback(() => {
    setState((previous) => ({ ...previous, refreshNonce: previous.refreshNonce + 1 }))
  }, [])

  const timeRange = useMemo<AdminMetricsTimeRange>(
    () => computeTimeRange(state),
    // refresh 시 timeRange도 재평가되도록 nonce 의존.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [state.preset, state.customFrom, state.customTo, state.refreshNonce],
  )

  return {
    state,
    timeRange,
    setPreset,
    setCustomRange,
    setAutoRefresh,
    refresh,
  }
}

export type UseMetricsFiltersReturn = ReturnType<typeof useMetricsFilters>
