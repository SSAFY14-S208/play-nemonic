'use client'

import type { ReactNode } from 'react'

type Props = {
  title: string
  children: ReactNode
}

// 섹션 헤더 + 12-col grid wrapper.

export function AnalyticsSection({ title, children }: Props) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="h3-b text-fg-primary">{title}</h2>
      <div className="grid grid-cols-12 gap-4">{children}</div>
    </section>
  )
}
