'use client'

import { AlertCircle, RefreshCw } from 'lucide-react'
import type { ReactNode } from 'react'

type Props = {
  isLoading: boolean
  errorMessage: string | null
  isEmpty: boolean
  onRetry?: () => void
  children: ReactNode
}

// 차트 panel 공통 wrapper — 로딩/에러/빈 상태 표준화.

export function ChartFrame({
  isLoading,
  errorMessage,
  isEmpty,
  onRetry,
  children,
}: Props) {
  if (errorMessage) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 p-6 text-center">
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

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center p-6">
        <p className="caption-r text-fg-secondary">불러오는 중…</p>
      </div>
    )
  }

  if (isEmpty) {
    return (
      <div className="flex h-full items-center justify-center p-6">
        <p className="caption-r text-fg-disabled">기간 내 데이터가 없습니다</p>
      </div>
    )
  }

  return <div className="relative h-full w-full">{children}</div>
}
