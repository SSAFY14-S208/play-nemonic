import { CheckCircle2, Home, Pin, Share2 } from 'lucide-react'

import { cn } from '@/shared/libs'

import { useFortuneSessionStore } from '../fortuneSessionStore'
import { useFortuneExternalShare } from '../hooks'
import type { FortuneScoreSet } from '../types'

const FORTUNE_CARD_TEMPLATE_PATH = '/images/fortune/templates/daily-fortune-card.png'

const TEMPLATE_CARD_CLASS = cn(
  'relative isolate mx-auto w-full max-w-[min(92vw,620px)] overflow-hidden',
  'aspect-[771/895] rotate-[-0.2deg]',
  'drop-shadow-[0_1.75rem_2.6rem_rgba(74,53,27,0.22)]',
  'animate-fortune-postit-land motion-reduce:animate-none',
)

const CARD_TEXT_BASE_CLASS = cn(
  'absolute z-[2] text-center tracking-normal [word-break:keep-all]',
  '[font-family:var(--font-fortune-hand)] text-[#15110a]',
)

const SCORE_VALUE_CLASS = cn(
  CARD_TEXT_BASE_CLASS,
  'top-[56.6%] -translate-x-1/2',
  'text-[clamp(1.5rem,5vw,2.25rem)] leading-none font-bold',
)

const ACTION_BUTTON_CLASS = cn(
  'body-b flex min-h-12 items-center justify-center gap-2 rounded-[var(--radius-md)]',
  'border border-fortune-border bg-fortune-paper px-4 text-fortune-accent-strong',
  'shadow-[inset_0_0.08rem_0_rgba(255,255,255,0.72),0_0.65rem_1.35rem_rgba(83,50,102,0.08)]',
)

const PRIMARY_ACTION_BUTTON_CLASS = cn(
  'body-b relative flex min-h-12 items-center justify-center gap-2 overflow-hidden rounded-[var(--radius-md)] border-0 px-4 text-fortune-inverse',
  'bg-fortune-accent bg-[linear-gradient(180deg,rgba(255,255,255,0.22),rgba(255,255,255,0))]',
  'shadow-[0_0.8rem_1.7rem_rgba(88,52,129,0.24),0_0_0_0.2rem_rgba(255,234,160,0.2),inset_0_0.1rem_0_rgba(255,255,255,0.3)]',
  "after:content-[''] after:absolute after:inset-0 after:-translate-x-[120%] after:skew-x-[-18deg]",
  'after:bg-[linear-gradient(90deg,rgba(255,255,255,0),rgba(255,255,255,0.35),rgba(255,255,255,0))]',
  'after:transition-transform after:duration-[420ms] after:ease hover:after:translate-x-[120%]',
  'motion-reduce:after:transition-none',
)

const SCORE_POSITIONS = [
  { key: 'love', left: '18.4%', color: '#ff5f95' },
  { key: 'work', left: '39.8%', color: '#16a9ee' },
  { key: 'money', left: '59.8%', color: '#ff9600' },
  { key: 'overall', left: '80.2%', color: '#3c8424' },
] as const satisfies readonly { key: keyof FortuneScoreSet; left: string; color: string }[]

interface FortuneResultCardProps {
  onAttach: () => void
  onBackToHub: () => void
}

export default function FortuneResultCard({ onAttach, onBackToHub }: FortuneResultCardProps) {
  const result = useFortuneSessionStore((state) => state.result)
  const { canShareExternal, isSharingExternal, shareExternal } = useFortuneExternalShare()

  if (!result) {
    return null
  }

  return (
    <section className="mx-auto grid w-[min(94vw,760px)] gap-5">
      <article className={TEMPLATE_CARD_CLASS} aria-label="오늘의 운세 카드">
        <img
          src={FORTUNE_CARD_TEMPLATE_PATH}
          alt=""
          aria-hidden
          className="absolute inset-0 z-0 size-full select-none object-contain"
          draggable={false}
        />

        <time className={cn(CARD_TEXT_BASE_CLASS, 'left-1/2 top-[8.8%] -translate-x-1/2 text-[clamp(1.05rem,3.3vw,1.9rem)] font-bold leading-none')}>
          {formatFortuneDate(result.issuedDateKey)}
        </time>

        <h1
          className={cn(
            CARD_TEXT_BASE_CLASS,
            'left-1/2 top-[17.4%] w-[64%] -translate-x-1/2',
            'text-[clamp(1.65rem,5.3vw,2.75rem)] font-bold leading-[1.18]',
          )}
        >
          {result.title}
        </h1>

        <p
          className={cn(
            CARD_TEXT_BASE_CLASS,
            'left-1/2 top-[37.4%] w-[74%] -translate-x-1/2',
            'text-[clamp(0.9rem,2.55vw,1.28rem)] font-bold leading-[1.28] text-[#4b3823]',
          )}
        >
          {result.postitLine}
        </p>

        {SCORE_POSITIONS.map((scorePosition) => (
          <strong
            key={scorePosition.key}
            className={SCORE_VALUE_CLASS}
            style={{ left: scorePosition.left, color: scorePosition.color }}
          >
            {result.scores[scorePosition.key]}
          </strong>
        ))}

        <div
          className={cn(
            CARD_TEXT_BASE_CLASS,
            'left-[22.2%] top-[78.5%] w-[26%] -translate-x-1/2 text-left',
            'text-[clamp(0.78rem,2.3vw,1.1rem)] font-bold leading-[1.45]',
          )}
        >
          <span className="mb-[0.18rem] inline-block size-[1.4em] rounded-full border border-[rgba(40,40,40,0.18)] align-middle shadow-[inset_0_0_0_0.14rem_rgba(255,255,255,0.55)]" style={{ background: result.luckyColor.hex }} aria-hidden />
          <span className="ml-[0.42rem] align-middle">{result.luckyColor.name}</span>
          <strong className="mt-[0.32rem] block text-center text-[clamp(0.95rem,2.7vw,1.25rem)]">{result.luckyKeyword}</strong>
        </div>

        <p
          className={cn(
            CARD_TEXT_BASE_CLASS,
            'left-[72.2%] top-[79.2%] w-[32%] -translate-x-1/2 text-left',
            'text-[clamp(0.76rem,2.25vw,1.05rem)] font-bold leading-[1.5]',
          )}
        >
          {result.caution}
        </p>
      </article>

      <div className="rounded-[var(--radius-lg)] border border-fortune-border bg-fortune-paper/90 p-4 text-center shadow-[0_0.8rem_1.7rem_rgba(83,50,102,0.08)]">
        <p className="body-b m-0 text-fortune-accent-strong">{result.summary}</p>
        <details className="mt-3 text-left">
          <summary className="caption-b w-fit cursor-pointer text-fortune-muted">입력한 사주 정보</summary>
          <p className="body-s mt-2 text-fortune-muted">{result.sajuSummary}</p>
        </details>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className={ACTION_BUTTON_CLASS} role="status">
          <CheckCircle2 className="size-4" aria-hidden />
          갤러리 저장 완료
        </div>
        <button type="button" className={ACTION_BUTTON_CLASS} onClick={onAttach}>
          <Pin className="size-4" aria-hidden />
          커뮤니티 게시
        </button>
        <button
          type="button"
          disabled={!canShareExternal}
          className={cn(ACTION_BUTTON_CLASS, 'disabled:cursor-not-allowed disabled:opacity-50')}
          onClick={shareExternal}
        >
          <Share2 className="size-4" aria-hidden />
          {isSharingExternal ? '공유 준비 중...' : '외부 공유'}
        </button>
        <button type="button" className={PRIMARY_ACTION_BUTTON_CLASS} onClick={onBackToHub}>
          <Home className="size-4" aria-hidden />
          광장으로 돌아가기
        </button>
      </div>
    </section>
  )
}

function formatFortuneDate(dateKey: string) {
  const dateParts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateKey)

  if (!dateParts) {
    return dateKey
  }

  const date = new Date(Number(dateParts[1]), Number(dateParts[2]) - 1, Number(dateParts[3]))
  const weekday = new Intl.DateTimeFormat('ko-KR', { weekday: 'short' }).format(date).replace('.', '')

  return `${Number(dateParts[2])}/${Number(dateParts[3])} (${weekday})`
}
