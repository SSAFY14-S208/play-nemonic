import { CheckCircle2, Home, Pin, Share2 } from 'lucide-react'

import { cn } from '@/shared/libs'

import { useFortuneSessionStore } from '../fortuneSessionStore'
import { useFortuneExternalShare } from '../hooks'
import type { FortuneScoreSet } from '../types'

const FORTUNE_CARD_TEMPLATE_PATH = '/images/fortune/templates/daily-fortune-card.png'
const FORTUNE_DIRECTION_ARROW_PATH = '/images/fortune/templates/arrow.png'

const TEMPLATE_CARD_CLASS = cn(
  'relative isolate mx-auto w-full max-w-[min(92vw,620px)] overflow-hidden',
  'aspect-[771/895] rotate-[-0.2deg]',
  'drop-shadow-[0_1.75rem_2.6rem_rgba(74,53,27,0.22)]',
  'animate-fortune-postit-land motion-reduce:animate-none',
)

const CARD_TEXT_BASE_CLASS = cn(
  'absolute z-[2] text-center tracking-normal [word-break:keep-all]',
  'font-fortune-hand text-[#15110a]',
)

const SCORE_VALUE_CLASS = cn(
  CARD_TEXT_BASE_CLASS,
  'top-[63.2%] -translate-x-1/2',
  'text-[clamp(1.28rem,4.2vw,1.88rem)] leading-none font-bold',
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

        <time className={cn(CARD_TEXT_BASE_CLASS, 'left-1/2 top-[9.3%] flex h-[5.4%] w-[22%] -translate-x-1/2 -translate-y-1/2 items-center justify-center text-[clamp(1.05rem,3.3vw,1.9rem)] font-bold leading-none')}>
          {formatFortuneDate(result.issuedDateKey)}
        </time>

        <h1
          className={cn(
            CARD_TEXT_BASE_CLASS,
            'left-1/2 top-[18.95%] w-[64%] -translate-x-1/2',
            'text-[clamp(1.58rem,5vw,2.58rem)] font-bold leading-[1.2]',
          )}
        >
          {result.title}
        </h1>

        <p
          className={cn(
            CARD_TEXT_BASE_CLASS,
            'left-1/2 top-[43.2%] flex h-[8%] w-[70%] -translate-x-1/2 -translate-y-1/2 items-center justify-center',
            'text-[clamp(0.82rem,2.25vw,1.12rem)] font-semibold leading-[1.35] text-[#4b3823]',
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
            'left-[24%] top-[83.4%] w-[34%] -translate-x-1/2 text-center',
            'text-[clamp(0.68rem,1.9vw,0.92rem)] font-semibold leading-[1.35]',
          )}
        >
          <div className="grid grid-cols-2 items-end gap-x-[8%] gap-y-[0.32rem]">
            <span className="mx-auto block size-[1.44em] rounded-full border border-[rgba(40,40,40,0.18)] shadow-[inset_0_0_0_0.14rem_rgba(255,255,255,0.55)]" style={{ background: result.luckyColor.hex }} aria-hidden />
            <img
              src={FORTUNE_DIRECTION_ARROW_PATH}
              alt=""
              aria-hidden
              className="mx-auto w-[clamp(1.7rem,5.6vw,2.65rem)] select-none object-contain drop-shadow-[0_0.12rem_0_rgba(255,255,255,0.76)]"
              draggable={false}
              style={{ transform: `rotate(${getDirectionArrowRotation(result.luckyDirection)}deg)` }}
            />
            <span className="block">{result.luckyColor.name}</span>
            <strong className="block text-[clamp(0.78rem,2.15vw,1.02rem)]">{result.luckyDirection}</strong>
          </div>
        </div>

        <p
          className={cn(
            CARD_TEXT_BASE_CLASS,
            'left-[75.8%] top-[84.6%] w-[30%] -translate-x-1/2 text-center',
            'text-[clamp(0.62rem,1.7vw,0.82rem)] font-semibold leading-[1.5]',
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

function getDirectionArrowRotation(direction: string) {
  if (direction.includes('북동')) return -45
  if (direction.includes('남동')) return 45
  if (direction.includes('남서')) return 135
  if (direction.includes('북서')) return -135
  if (direction.includes('북')) return -90
  if (direction.includes('남')) return 90
  if (direction.includes('서')) return 180

  return 0
}
