import { RELAY_RESULT_REVEALS, type RelayResultReveal } from '../../constants'
import { cn } from '@/shared/libs'

interface ResultProgressStripProps {
  activeReveal: RelayResultReveal
  activeRevealIndex: number
}

// 결과 화면 상단 — 날짜/앨범 라벨 + 단계 dot 진행도.
export default function ResultProgressStrip({
  activeReveal,
  activeRevealIndex,
}: ResultProgressStripProps) {
  return (
    <div className="flex min-h-9 items-center justify-between gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="caption-b text-relay-accent-strong">2026.04.28 ·</span>
        <span className="caption-b rounded-full bg-relay-active px-3 py-1 text-relay-ink">
          🐱 고양이 님의 앨범
        </span>
      </div>

      <div className="flex items-center gap-1.5">
        {RELAY_RESULT_REVEALS.map((reveal, revealIndex) => (
          <span
            key={reveal.key}
            className={cn(
              'size-[9px] rounded-full bg-relay-line',
              revealIndex <= activeRevealIndex && 'bg-relay-accent-strong',
            )}
          />
        ))}
        <span className="caption-b ml-1 text-relay-accent-strong">
          {activeReveal.order} / {RELAY_RESULT_REVEALS.length}
        </span>
      </div>
    </div>
  )
}
