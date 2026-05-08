import type { FortuneResult } from '../types'

interface FortuneLimitNoticeProps {
  nextResetLabel: string
  result: FortuneResult | null
  showResetAction?: boolean
  onReset: () => void
  onShowResult: () => void
}

export default function FortuneLimitNotice({
  nextResetLabel,
  result,
  showResetAction = false,
  onReset,
  onShowResult,
}: FortuneLimitNoticeProps) {
  return (
    <section className="fortune-floating-panel grid gap-5 rounded-[var(--radius-xl)] border border-fortune-border bg-fortune-panel p-6 text-center shadow-soft-lg">
      <p className="caption-b text-fortune-muted">{nextResetLabel}</p>
      <h1 className="h2-b text-fortune-ink">오늘의 운세 메모는 이미 받았어요</h1>
      <p className="body-r text-fortune-muted">
        하루에 한 번만 뽑을 수 있어요. 오늘 받은 메모는 다시 볼 수 있습니다.
      </p>
      <button
        type="button"
        disabled={!result}
        className="body-b fortune-primary-button min-h-12 rounded-[var(--radius-md)] bg-fortune-accent px-4 text-fortune-inverse transition hover:-translate-y-0.5 disabled:translate-y-0 disabled:bg-fortune-disabled"
        onClick={onShowResult}
      >
        오늘 받은 운세 다시 보기
      </button>
      {showResetAction && (
        <button
          type="button"
          className="body-b fortune-secondary-button min-h-12 rounded-[var(--radius-md)] border border-fortune-border bg-fortune-paper px-4 text-fortune-ink transition hover:bg-fortune-glow"
          onClick={onReset}
        >
          테스트용으로 처음부터 보기
        </button>
      )}
    </section>
  )
}
