import { CheckCircle2, Home, Pin, Sparkles, Star } from 'lucide-react'

import { FORTUNE_SCORE_LABELS } from '../constants'
import { useFortuneSessionStore } from '../fortuneSessionStore'

interface FortuneResultCardProps {
  onAttach: () => void
  onBackToHub: () => void
}

export default function FortuneResultCard({ onAttach, onBackToHub }: FortuneResultCardProps) {
  const result = useFortuneSessionStore((state) => state.result)

  if (!result) {
    return null
  }

  return (
    <section className="mx-auto w-[min(92vw,760px)] max-md:w-[min(94vw,560px)] grid gap-5">
      <article className="fortune-postit-sheet" data-theme={result.cardTheme} aria-label="오늘의 운세 포스트잇">
        <div className="fortune-postit-tape" aria-hidden>
          오늘의 운세
        </div>
        <div className="fortune-popo-sticker" aria-hidden>
          <Sparkles className="size-4" />
          <span>포포</span>
        </div>
        <div className="fortune-star-sticker fortune-star-sticker-left" aria-hidden>
          <Star className="size-5" />
        </div>
        <div className="fortune-star-sticker fortune-star-sticker-right" aria-hidden>
          <Star className="size-4" />
        </div>
        <div className="fortune-moon-sticker" aria-hidden />
        <div className="fortune-postit-save-stamp" role="status">
          <CheckCircle2 className="size-4" aria-hidden />
          갤러리 저장
        </div>

        <header className="fortune-postit-header grid gap-[0.35rem] max-w-md mx-auto text-center">
          <p className="caption-b text-fortune-muted">네모닉 운세 메모</p>
          <h1>{result.title}</h1>
        </header>

        <div className="fortune-postit-line-block">
          <p className="caption-b">포포의 한 줄</p>
          <p>{result.postitLine}</p>
        </div>

        <p className="fortune-postit-summary">{result.summary}</p>

        <div className="grid grid-cols-4 gap-[clamp(0.72rem,1.8vw,1rem)] mt-[clamp(1.1rem,2.5vw,1.65rem)] max-md:grid-cols-2" aria-label="운세 점수">
            {FORTUNE_SCORE_LABELS.map((scoreLabel) => (
              <div key={scoreLabel.key} className="fortune-score-sticker">
                <div className="flex items-baseline justify-between gap-[0.6rem] text-fortune-ink">
                  <span className="text-[0.82rem] font-extrabold tracking-normal">{scoreLabel.label}</span>
                  <strong className="font-[family-name:var(--font-fortune-serif)] text-[1.45rem] font-normal tracking-normal">{result.scores[scoreLabel.key]}</strong>
                </div>
                <div className="mt-[0.82rem] h-[0.5rem] overflow-hidden rounded-full bg-[rgba(255,255,255,0.62)] shadow-[inset_0_0.08rem_0.18rem_rgba(56,37,68,0.08)]" aria-hidden>
                  <span className="block h-full rounded-[inherit] bg-[linear-gradient(90deg,#9f82df,#ffd65d)]" style={{ width: `${result.scores[scoreLabel.key]}%` }} />
                </div>
              </div>
            ))}
        </div>

        <div className="grid grid-cols-2 gap-[clamp(0.7rem,1.7vw,1rem)] mt-[clamp(1rem,2.5vw,1.4rem)]">
          <div className="fortune-label-sticker">
            <span>행운 키워드</span>
            <strong>{result.luckyKeyword}</strong>
          </div>
          <div className="fortune-color-sticker">
            <span className="fortune-color-sticker-swatch" style={{ background: result.luckyColor.hex }} aria-hidden />
            <div>
              <span>행운 색</span>
              <strong>{result.luckyColor.name}</strong>
            </div>
          </div>
        </div>

        <p className="fortune-caution-sticker">
          <span>주의 메모</span>
          {' '}
          {result.caution}
        </p>

        <details className="fortune-saju-receipt">
          <summary>입력한 사주 정보</summary>
          <p>{result.sajuSummary}</p>
          <dl className="fortune-saju-pillar-list">
            {[
              ['년주', result.saju.sajuYear],
              ['월주', result.saju.sajuMonth],
              ['일주', result.saju.sajuDay],
              ['시주', result.saju.sajuHour],
            ].map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
          <p>
            일간 {result.saju.dayElemental}/{result.saju.dayYinYang} · 일지{' '}
            {result.saju.dayBranchElemental}/{result.saju.dayBranchYinYang}
          </p>
        </details>
      </article>

      <div className="grid gap-3 sm:grid-cols-3">
        <div
          className="body-b flex min-h-12 items-center justify-center gap-2 rounded-[var(--radius-md)] border border-fortune-border bg-fortune-paper px-4 text-fortune-accent-strong shadow-[inset_0_0.08rem_0_rgba(255,255,255,0.72),0_0.65rem_1.35rem_rgba(83,50,102,0.08)]"
          role="status"
        >
          <CheckCircle2 className="size-4" aria-hidden />
          갤러리 저장 완료
        </div>
        <button
          type="button"
          className="body-b flex min-h-12 items-center justify-center gap-2 rounded-[var(--radius-md)] border border-fortune-border bg-fortune-paper px-4 text-fortune-accent-strong shadow-[inset_0_0.08rem_0_rgba(255,255,255,0.72),0_0.65rem_1.35rem_rgba(83,50,102,0.08)]"
          onClick={onAttach}
        >
          <Pin className="size-4" aria-hidden />
          커뮤니티 게시
        </button>
        <button
          type="button"
          className="body-b fortune-primary-button flex min-h-12 items-center justify-center gap-2 rounded-[var(--radius-md)] bg-fortune-accent px-4 text-fortune-inverse"
          onClick={onBackToHub}
        >
          <Home className="size-4" aria-hidden />
          광장으로 돌아가기
        </button>
      </div>
    </section>
  )
}
