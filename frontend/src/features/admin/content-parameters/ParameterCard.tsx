import type { ReactNode } from 'react'

import { cn } from '@/shared/libs'

interface ParameterCardProps {
  categoryLabel: string
  categoryChipClass: string
  title: string
  description: string
  originalValueLabel: string
  /** 우측 입력 영역 — 정수/범위/enum 등 페이지에서 조립한 input JSX. */
  children: ReactNode
}

export function ParameterCard({
  categoryLabel,
  categoryChipClass,
  title,
  description,
  originalValueLabel,
  children,
}: ParameterCardProps) {
  return (
    <article className="flex items-start justify-between gap-6 rounded-[var(--radius-lg)] border border-border-default bg-surface-default p-6">
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <span
          className={cn(
            'caption-b inline-flex w-fit items-center rounded-[var(--radius-sm)] px-2 py-0.5',
            categoryChipClass,
          )}
        >
          {categoryLabel}
        </span>
        <h3 className="h4-b text-fg-primary">{title}</h3>
        <p className="body-r text-fg-secondary">{description}</p>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-2">
        {children}
        <span className="caption-r text-fg-disabled">{originalValueLabel}</span>
      </div>
    </article>
  )
}
