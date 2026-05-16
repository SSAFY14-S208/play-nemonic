'use client'

import { AlertCircle, RefreshCw } from 'lucide-react'

import { cn } from '@/shared/libs'

type KpiTile = {
  label: string
  value: string | number
  // 색은 OSD 마케팅 대시보드의 의미 색 — danger/warn/neutral/good/great/accent/muted.
  // CSS variable로 받아 chart 전용 팔레트(`constants.ts`)와 연결.
  accentColor?: string
  hint?: string
}

type Props = {
  tiles: KpiTile[]
  isLoading: boolean
  errorMessage: string | null
  onRetry?: () => void
  // 한 줄에 보여줄 타일 수 — Tailwind grid cols.
  columns?: 2 | 3 | 4
}

const formatNumber = (value: number) => value.toLocaleString('ko-KR')

const renderValue = (value: string | number) => {
  if (typeof value === 'number') return formatNumber(value)
  return value
}

export function AnalyticsKpiCard({
  tiles,
  isLoading,
  errorMessage,
  onRetry,
  columns = 4,
}: Props) {
  if (errorMessage) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 p-6">
        <AlertCircle className="h-6 w-6 text-red-500" aria-hidden />
        <p className="body-r text-fg-secondary">{errorMessage}</p>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="caption-b inline-flex items-center gap-1.5 rounded-[var(--radius-md)] border border-border-default bg-surface-default px-3 py-1.5 text-fg-primary transition-colors hover:bg-surface-subtle"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            새로고침
          </button>
        )}
      </div>
    )
  }

  const gridClass =
    columns === 2
      ? 'grid-cols-2'
      : columns === 3
        ? 'grid-cols-3'
        : 'grid-cols-2 md:grid-cols-4'

  return (
    <div className={cn('grid h-full gap-4 p-4', gridClass)}>
      {tiles.map((tile) => (
        <div
          key={tile.label}
          className="flex flex-col items-center justify-center gap-1 rounded-[var(--radius-md)] bg-surface-subtle p-4 text-center"
        >
          <p className="caption-b text-fg-secondary">{tile.label}</p>
          <p
            className="h1-b"
            style={{
              color: tile.accentColor ?? 'var(--color-fg-primary, currentColor)',
              opacity: isLoading ? 0.45 : 1,
            }}
          >
            {isLoading ? '—' : renderValue(tile.value)}
          </p>
          {tile.hint && (
            <p className="caption-r text-fg-disabled">{tile.hint}</p>
          )}
        </div>
      ))}
    </div>
  )
}
