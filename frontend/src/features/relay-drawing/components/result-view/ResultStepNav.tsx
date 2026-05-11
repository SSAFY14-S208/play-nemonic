import type { RelayResultReveal } from '../../constants'
import RelayButton from '../RelayButton'

interface ResultStepNavProps {
  activeReveal: RelayResultReveal
  activeRevealIndex: number
  revealCount: number
  canShowPreviousResultReveal: boolean
  canShowNextResultReveal: boolean
  onShowPreviousResultReveal: () => void
  onShowNextResultReveal: () => void
}

// 결과 단계 ◀ / ▶ 네비게이션 + 진행 바.
export default function ResultStepNav({
  activeReveal,
  activeRevealIndex,
  revealCount,
  canShowPreviousResultReveal,
  canShowNextResultReveal,
  onShowPreviousResultReveal,
  onShowNextResultReveal,
}: ResultStepNavProps) {
  const progressPercent = ((activeRevealIndex + 1) / revealCount) * 100

  return (
    <div className="mt-4 flex min-h-11 items-center gap-3">
      <RelayButton
        variant="secondary"
        onClick={onShowPreviousResultReveal}
        disabled={!canShowPreviousResultReveal}
        className="rounded-xl border-[1.5px]"
      >
        ◀ 이전
      </RelayButton>

      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-relay-credit-row">
        <div
          className="h-full rounded-full bg-relay-accent-strong"
          style={{ width: `${progressPercent}%` }}
        />
      </div>

      <RelayButton
        onClick={onShowNextResultReveal}
        disabled={!canShowNextResultReveal}
        className="rounded-xl"
      >
        {activeReveal.nextLabel}
      </RelayButton>
    </div>
  )
}
