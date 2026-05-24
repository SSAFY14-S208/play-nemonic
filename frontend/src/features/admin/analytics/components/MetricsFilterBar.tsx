'use client'

import { RefreshCw } from 'lucide-react'
import type { ChangeEvent } from 'react'

import { cn } from '@/shared/libs'

import { METRICS_TIME_RANGE_PRESETS } from '..'
import type { MetricsFiltersState, MetricsTimeRangePresetKey } from '../types'

type Props = {
  state: MetricsFiltersState
  onPresetChange: (preset: MetricsTimeRangePresetKey) => void
  onCustomRangeChange: (customFrom: string, customTo: string) => void
  onAutoRefreshChange: (enabled: boolean) => void
  onRefresh: () => void
}

// 시스템 옵저버빌리티용 필터 바. dashboard 필터 바보다 단순 — service 멀티 셀렉트
// 없고 시간 프리셋만.

export function MetricsFilterBar({
  state,
  onPresetChange,
  onCustomRangeChange,
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
          {METRICS_TIME_RANGE_PRESETS.map((preset) => {
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

      <div className="ml-auto flex items-center gap-3">
        <label className="caption-b inline-flex items-center gap-1.5 text-fg-secondary">
          <input
            type="checkbox"
            checked={state.autoRefresh}
            onChange={(event) => onAutoRefreshChange(event.target.checked)}
            className="h-3.5 w-3.5 cursor-pointer accent-primary-1"
          />
          15초 자동 갱신
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
