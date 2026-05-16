'use client'

import { Construction } from 'lucide-react'

type Props = {
  title: string
  subtitle: string
}

// 백엔드 확장 후 활성화 placeholder.
// 각 viz가 요구하는 ES aggregation은 현 백엔드 API(field-summary 8종, histogram byLevel)
// 로 채울 수 없어 자리만 잡아둠. 사용자 보고에 제공한 백엔드 확장 항목 표가 머지되면
// 후속 PR에서 실제 차트로 교체된다.

export function PendingVizCard({ title, subtitle }: Props) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-2 p-6 text-center">
      <Construction className="h-7 w-7 text-fg-disabled" aria-hidden />
      <p className="body-l-b text-fg-secondary">{title}</p>
      <p className="caption-r text-fg-disabled">{subtitle}</p>
      <p className="caption-r mt-2 text-fg-disabled">
        백엔드 확장 후 활성화됩니다
      </p>
    </div>
  )
}
