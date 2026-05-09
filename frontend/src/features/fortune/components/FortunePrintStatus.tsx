import { useFortuneSessionStore } from '../fortuneSessionStore'

export default function FortunePrintStatus() {
  const isPrinting = useFortuneSessionStore((state) => state.step === 'printing')

  return (
    <section className="fortune-floating-panel rounded-[var(--radius-xl)] border border-fortune-border bg-fortune-panel p-6 text-center shadow-soft-lg">
      <div className="fortune-print-sigil" aria-hidden />
      <p className="caption-b text-fortune-muted">3 / 3</p>
      <h1 className="h2-b mt-1 text-fortune-ink">
        {isPrinting ? '오늘의 기운을 메모에 담는 중' : '포포가 메모를 준비하고 있어요'}
      </h1>
      <p className="body-r mt-3 text-fortune-muted">
        네모닉 프린터에서 작은 운세 메모가 천천히 밀려 나옵니다.
      </p>
    </section>
  )
}
