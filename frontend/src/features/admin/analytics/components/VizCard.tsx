'use client'

import type { ReactNode } from 'react'

import { cn } from '@/shared/libs'

type Props = {
  title: string
  subtitle?: string
  // 우측 상단 작은 라벨 (예: panel id).
  badge?: string
  children: ReactNode
  span?: 3 | 4 | 6 | 8 | 12
  minHeight?: number
}

// panel 공통 컨테이너. 12-col grid의 셀.

export function VizCard({
  title,
  subtitle,
  badge,
  children,
  span = 6,
  minHeight = 220,
}: Props) {
  const colSpan =
    span === 3
      ? 'md:col-span-6 lg:col-span-3'
      : span === 4
        ? 'md:col-span-6 lg:col-span-4'
        : span === 6
          ? 'md:col-span-6'
          : span === 8
            ? 'md:col-span-8'
            : 'col-span-12'

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
          {subtitle && <p className="caption-r text-fg-secondary">{subtitle}</p>}
        </div>
        {badge && <span className="caption-b text-fg-disabled">{badge}</span>}
      </header>
      <div className="flex flex-1 flex-col">{children}</div>
    </article>
  )
}
