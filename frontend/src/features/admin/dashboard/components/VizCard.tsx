'use client'

import type { ReactNode } from 'react'

import { cn } from '@/shared/libs'

type Props = {
  vizId: string
  title: string
  subtitle: string
  children: ReactNode
  // grid에서 차지할 열 수 (12-col 기준).
  span?: 4 | 6 | 8 | 12
  minHeight?: number
}

// viz panel 공통 컨테이너 — 12-col grid의 셀 + 헤더 + body 영역.

export function VizCard({
  vizId,
  title,
  subtitle,
  children,
  span = 6,
  minHeight = 220,
}: Props) {
  const colSpan =
    span === 4
      ? 'lg:col-span-4'
      : span === 6
        ? 'lg:col-span-6'
        : span === 8
          ? 'lg:col-span-8'
          : 'lg:col-span-12'

  return (
    <article
      className={cn(
        'col-span-12 flex flex-col overflow-hidden rounded-[var(--radius-lg)] border border-border-default bg-surface-default shadow-sm',
        colSpan,
      )}
      style={{ minHeight }}
    >
      <header className="flex shrink-0 items-baseline justify-between border-b border-border-default px-4 py-3">
        <div className="flex flex-col gap-0.5">
          <p className="h4-b text-fg-primary">{title}</p>
          <p className="caption-r text-fg-secondary">{subtitle}</p>
        </div>
        <span className="caption-b text-fg-disabled">{vizId}</span>
      </header>
      <div className="flex flex-1 flex-col">{children}</div>
    </article>
  )
}
