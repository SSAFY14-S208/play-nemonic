import { type ReactNode } from 'react'

import { cn } from '@/shared/libs'

interface LabelPaperCardProps {
  children: ReactNode
  className?: string
}

// 네모닉 프린터로 출력된 라벨지 한 장을 모사하는 카드.
// 본체는 흰 종이 + 둥근 모서리 + 부드러운 그림자, 상단에는 프린터의 절취선/티켓
// 가장자리 느낌을 주는 점선 띠를 둔다. children 영역이 라벨에 인쇄된 콘텐츠.
//
// 결과 화면에서는 이 카드 위에 사용자가 그린 드로잉 PNG가 얹혀, 백엔드가
// alpha-PNG를 내려주면 종이 결이 그대로 비치며 "라벨에 출력된 그림" 느낌이 된다.
export default function LabelPaperCard({
  children,
  className,
}: LabelPaperCardProps) {
  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-lg bg-relay-paper shadow-[0_8px_18px_rgba(184,121,22,0.18),0_2px_4px_rgba(184,121,22,0.12)]',
        className,
      )}
    >
      {/* 상단 점선 띠 — 프린터 절취선 모사. aria-hidden 장식. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 h-2 bg-[repeating-linear-gradient(90deg,transparent_0_5px,rgba(212,156,31,0.35)_5px_10px)]"
      />
      {/* 좌우 가장자리 미세 그라데이션 — 종이 결 입체감. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-y-0 left-0 w-1.5 bg-[linear-gradient(90deg,rgba(184,121,22,0.08),transparent)]"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-y-0 right-0 w-1.5 bg-[linear-gradient(-90deg,rgba(184,121,22,0.08),transparent)]"
      />
      {/* 인쇄 콘텐츠 영역 — 상단 띠를 피해 약간의 inset. */}
      <div className="relative h-full w-full pt-2">{children}</div>
    </div>
  )
}
