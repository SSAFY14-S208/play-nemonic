'use client'

import { METRICS_COLORS } from '../constants'

type Props = {
  label: string
  status: 'up' | 'down' | 'unknown'
}

const colorFor = (status: Props['status']): string => {
  if (status === 'up') return METRICS_COLORS.great
  if (status === 'down') return METRICS_COLORS.danger
  return METRICS_COLORS.muted
}

const labelFor = (status: Props['status']): string => {
  if (status === 'up') return 'UP'
  if (status === 'down') return 'DOWN'
  return '—'
}

// 서비스 up/down 신호등 dot. 큰 색 dot + 서비스명 + 상태 텍스트.

export function ServiceStatusDot({ label, status }: Props) {
  const color = colorFor(status)
  return (
    <div className="flex h-full flex-col items-center justify-center gap-2 p-3">
      <span
        className="inline-block h-4 w-4 rounded-full"
        style={{ backgroundColor: color }}
        aria-hidden
      />
      <p className="caption-b text-fg-primary">{label}</p>
      <p className="caption-r" style={{ color }}>
        {labelFor(status)}
      </p>
    </div>
  )
}
