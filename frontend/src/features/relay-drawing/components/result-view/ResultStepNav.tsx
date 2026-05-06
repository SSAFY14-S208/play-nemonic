import { RELAY_RESULT_REVEALS, type RelayResultReveal } from '../../constants'

interface ResultStepNavProps {
  activeReveal: RelayResultReveal
  activeRevealIndex: number
  canShowPreviousResultReveal: boolean
  canShowNextResultReveal: boolean
  onShowPreviousResultReveal: () => void
  onShowNextResultReveal: () => void
}

// 결과 단계 ◀ / ▶ 네비게이션 + 진행 바.
export default function ResultStepNav({
  activeReveal,
  activeRevealIndex,
  canShowPreviousResultReveal,
  canShowNextResultReveal,
  onShowPreviousResultReveal,
  onShowNextResultReveal,
}: ResultStepNavProps) {
  const progressPercent = ((activeRevealIndex + 1) / RELAY_RESULT_REVEALS.length) * 100

  return (
    <div className="mt-4 flex min-h-11 items-center gap-3">
      <button
        type="button"
        onClick={onShowPreviousResultReveal}
        disabled={!canShowPreviousResultReveal}
        className="body-b min-h-11 rounded-[12px] border-[1.5px] border-relay-line bg-relay-paper px-4 text-relay-accent-strong disabled:opacity-45"
      >
        ◀ 이전
      </button>

      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-relay-credit-row">
        <div
          className="h-full rounded-full bg-relay-accent-strong"
          style={{ width: `${progressPercent}%` }}
        />
      </div>

      <button
        type="button"
        onClick={onShowNextResultReveal}
        disabled={!canShowNextResultReveal}
        className="body-b min-h-11 rounded-[12px] bg-relay-accent px-4 text-relay-ink disabled:opacity-45"
      >
        {activeReveal.nextLabel}
      </button>
    </div>
  )
}
