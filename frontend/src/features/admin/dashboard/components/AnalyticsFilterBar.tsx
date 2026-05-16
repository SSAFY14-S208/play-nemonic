'use client'

import { RefreshCw } from 'lucide-react'
import type { ChangeEvent } from 'react'

import { cn } from '@/shared/libs'

import { SERVICE_OPTIONS, TIME_RANGE_PRESETS } from '../constants'
import type {
  AnalyticsFiltersState,
  LogsTimeRangePresetKey,
} from '../types'

type Props = {
  state: AnalyticsFiltersState
  onPresetChange: (preset: LogsTimeRangePresetKey) => void
  onCustomRangeChange: (customFrom: string, customTo: string) => void
  onServiceToggle: (service: string) => void
  onAutoRefreshChange: (enabled: boolean) => void
  onRefresh: () => void
}

// 상단 sticky 필터 바 — 시간 범위 프리셋 + service 멀티 셀렉트 + 새로고침 + 자동 갱신 토글.

export function AnalyticsFilterBar({
  state,
  onPresetChange,
  onCustomRangeChange,
  onServiceToggle,
  onAutoRefreshChange,
  onRefresh,
}: Props) {
  const handleCustomFromChange = (event: ChangeEvent<HTMLInputElement>) => {
    onCustomRangeChange(event.target.value, state.customTo)
  }
  const handleCustomToChange = (event: ChangeEvent<HTMLInputElement>) => {
    onCustomRangeChange(state.customFrom, event.target.value)
  }

  return (
    <div className="sticky top-0 z-[var(--z-sticky)] flex flex-wrap items-center gap-3 border-b border-border-default bg-surface-default px-6 py-3">
      <div className="flex items-center gap-2">
        <span className="caption-b text-fg-secondary">시간 범위</span>
        <div
          role="radiogroup"
          aria-label="시간 범위 프리셋"
          className="flex overflow-hidden rounded-[var(--radius-md)] border border-border-default"
        >
          {TIME_RANGE_PRESETS.map((preset) => {
            const isActive = state.preset === preset.key
            return (
              <button
                key={preset.key}
                type="button"
                role="radio"
                aria-checked={isActive}
                onClick={() => onPresetChange(preset.key)}
                className={cn(
                  'caption-b border-r border-border-default px-3 py-1.5 transition-colors last:border-r-0',
                  isActive
                    ? 'bg-primary-5 text-primary-2'
                    : 'bg-surface-default text-fg-secondary hover:bg-surface-subtle',
                )}
              >
                {preset.label}
              </button>
            )
          })}
        </div>
      </div>

      {state.preset === 'custom' && (
        <div className="flex items-center gap-2">
          <input
            type="datetime-local"
            value={state.customFrom}
            onChange={handleCustomFromChange}
            aria-label="시작 시각"
            className="caption-r rounded-[var(--radius-md)] border border-border-default bg-surface-default px-2 py-1 text-fg-primary"
          />
          <span className="caption-r text-fg-secondary">→</span>
          <input
            type="datetime-local"
            value={state.customTo}
            onChange={handleCustomToChange}
            aria-label="종료 시각"
            className="caption-r rounded-[var(--radius-md)] border border-border-default bg-surface-default px-2 py-1 text-fg-primary"
          />
        </div>
      )}

      <div className="flex items-center gap-2">
        <span className="caption-b text-fg-secondary">service</span>
        <div className="flex gap-1">
          {SERVICE_OPTIONS.map((option) => {
            const isSelected = state.services.includes(option.value)
            return (
              <button
                key={option.value}
                type="button"
                aria-pressed={isSelected}
                onClick={() => onServiceToggle(option.value)}
                className={cn(
                  'caption-b rounded-[var(--radius-md)] border px-2.5 py-1 transition-colors',
                  isSelected
                    ? 'border-primary-2 bg-primary-5 text-primary-2'
                    : 'border-border-default bg-surface-default text-fg-secondary hover:bg-surface-subtle',
                )}
              >
                {option.label}
              </button>
            )
          })}
        </div>
      </div>

      <div className="ml-auto flex items-center gap-3">
        <label className="caption-b inline-flex items-center gap-1.5 text-fg-secondary">
          <input
            type="checkbox"
            checked={state.autoRefresh}
            onChange={(event) => onAutoRefreshChange(event.target.checked)}
            className="h-3.5 w-3.5 cursor-pointer accent-primary-1"
          />
          30초 자동 갱신
        </label>
        <button
          type="button"
          onClick={onRefresh}
          className="caption-b inline-flex items-center gap-1.5 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-1.5 text-fg-primary transition-colors hover:bg-surface-subtle"
          aria-label="새로고침"
        >
          <RefreshCw className="h-3.5 w-3.5" />
          새로고침
        </button>
      </div>
    </div>
  )
}
