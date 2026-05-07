import { WandSparkles } from 'lucide-react'
import { useMemo } from 'react'
import { useShallow } from 'zustand/react/shallow'

import { useFortuneSessionStore } from '../fortuneSessionStore'
import { calculateFortuneSaju, isBirthInfoComplete } from '../utils'

interface FortuneDrawPanelProps {
  onDraw: () => void
  onEdit: () => void
}

export default function FortuneDrawPanel({ onDraw, onEdit }: FortuneDrawPanelProps) {
  const { birthInfo, isDrawing } = useFortuneSessionStore(
    useShallow((state) => ({
      birthInfo: state.birthInfo,
      isDrawing: state.isDrawingFortune,
    })),
  )
  const saju = useMemo(() => {
    if (!isBirthInfoComplete(birthInfo)) {
      return null
    }

    try {
      return calculateFortuneSaju(birthInfo)
    } catch {
      return null
    }
  }, [birthInfo])
  const calendarLabel = birthInfo.calendarType === 'solar' ? '양력' : '음력'
  const timeLabel = birthInfo.timeUnknown ? '시간 모름' : birthInfo.birthTime

  return (
    <section className="fortune-floating-panel grid gap-5 rounded-[var(--radius-xl)] border border-fortune-border bg-fortune-panel p-6 shadow-soft-lg">
      <div>
        <p className="caption-b text-fortune-muted">2 / 3</p>
        <h1 className="h2-b mt-1 text-fortune-ink">버튼을 누르면 메모가 출력돼요</h1>
      </div>
      <div className="fortune-soft-inset rounded-[var(--radius-lg)] border border-fortune-border bg-fortune-paper p-4">
        <p className="caption-b text-fortune-muted">입력값</p>
        <p className="body-b mt-1 text-fortune-ink">
          {calendarLabel} {birthInfo.birthDate} {timeLabel}
        </p>
      </div>
      {saju && (
        <dl className="fortune-soft-inset grid grid-cols-2 gap-3 rounded-[var(--radius-lg)] border border-fortune-border bg-fortune-paper p-4 sm:grid-cols-4">
          {[
            ['년주', saju.sajuYear],
            ['월주', saju.sajuMonth],
            ['일주', saju.sajuDay],
            ['시주', saju.sajuHour],
          ].map(([label, value]) => (
            <div key={label} className="rounded-[var(--radius-md)] bg-fortune-glow px-3 py-2 text-center">
              <dt className="caption-b text-fortune-muted">{label}</dt>
              <dd className="body-b mt-1 text-fortune-ink">{value}</dd>
            </div>
          ))}
        </dl>
      )}
      <div className="grid gap-3 sm:grid-cols-[0.8fr_1.2fr]">
        <button
          type="button"
          className="body-b fortune-secondary-button min-h-12 rounded-[var(--radius-md)] border border-fortune-border bg-fortune-paper px-4 text-fortune-ink transition hover:bg-fortune-glow"
          onClick={onEdit}
        >
          수정하기
        </button>
        <button
          type="button"
          className="body-l-b fortune-primary-button flex min-h-14 items-center justify-center gap-2 rounded-[var(--radius-md)] bg-fortune-accent px-5 text-fortune-inverse shadow-soft-lg transition hover:-translate-y-0.5"
          disabled={isDrawing}
          onClick={onDraw}
        >
          <WandSparkles className="size-5" aria-hidden />
          {isDrawing ? '포포가 준비 중' : '운세 뽑기'}
        </button>
      </div>
    </section>
  )
}
