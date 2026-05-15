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
    <section className="fortune-result-scene grid gap-5">
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

        <header className="fortune-postit-header">
          <p className="caption-b">네모닉 운세 메모</p>
          <h1>{result.title}</h1>
        </header>

        <div className="fortune-postit-line-block">
          <p className="caption-b">포포의 한 줄</p>
          <p>{result.postitLine}</p>
        </div>

        <p className="fortune-postit-summary">{result.summary}</p>

        <div className="fortune-sticker-grid" aria-label="운세 점수">
            {FORTUNE_SCORE_LABELS.map((scoreLabel) => (
              <div key={scoreLabel.key} className="fortune-score-sticker">
                <div className="fortune-score-sticker-head">
                  <span>{scoreLabel.label}</span>
                  <strong>{result.scores[scoreLabel.key]}</strong>
                </div>
                <div className="fortune-score-meter" aria-hidden>
                  <span style={{ width: `${result.scores[scoreLabel.key]}%` }} />
                </div>
              </div>
            ))}
        </div>

        <div className="fortune-postit-sticker-row">
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
          className="body-b fortune-secondary-button flex min-h-12 items-center justify-center gap-2 rounded-[var(--radius-md)] border border-fortune-border bg-fortune-paper px-4 text-fortune-accent-strong"
          role="status"
        >
          <CheckCircle2 className="size-4" aria-hidden />
          갤러리 저장 완료
        </div>
        <button
          type="button"
          className="body-b fortune-secondary-button flex min-h-12 items-center justify-center gap-2 rounded-[var(--radius-md)] border border-fortune-border bg-fortune-paper px-4 text-fortune-accent-strong"
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
