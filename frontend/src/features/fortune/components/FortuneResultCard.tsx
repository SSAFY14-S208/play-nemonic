import { CheckCircle2, Home, Pin, Share2 } from 'lucide-react'
import { useEffect, useState } from 'react'

import { cn } from '@/shared/libs'

import { useFortuneSessionStore, createFortuneCommunityImageDataUrl } from '..'
import { useFortuneExternalShare } from '../hooks'


const FORTUNE_CARD_TEMPLATE_PATH = '/images/fortune/templates/daily-fortune-card.png'

const TEMPLATE_CARD_CLASS = cn(
  'relative isolate mx-auto w-full max-w-[min(92vw,620px)] overflow-hidden',
  'aspect-[771/895] rotate-[-0.2deg]',
  'drop-shadow-[0_1.75rem_2.6rem_rgba(74,53,27,0.22)]',
  'animate-fortune-postit-land motion-reduce:animate-none',
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

interface FortuneResultCardProps {
  onAttach: () => void
  onBackToHub: () => void
}

export default function FortuneResultCard({ onAttach, onBackToHub }: FortuneResultCardProps) {
  const result = useFortuneSessionStore((state) => state.result)
  const { canShareExternal, isSharingExternal, shareExternal } = useFortuneExternalShare()
  const [renderedCardImageUrl, setRenderedCardImageUrl] = useState<string | null>(null)

  useEffect(() => {
    let isMounted = true

    void (async () => {
      if (!result) {
        if (isMounted) {
          setRenderedCardImageUrl(null)
        }
        return
      }

      const imageUrl = await createFortuneCommunityImageDataUrl(result)
      if (!isMounted) {
        return
      }
      setRenderedCardImageUrl(imageUrl ?? result.fortuneImageUrl ?? null)
    })()

    return () => {
      isMounted = false
    }
  }, [result])

  if (!result) {
    return null
  }

  return (
    <section className="mx-auto grid w-[min(94vw,760px)] gap-5">
      <article className={TEMPLATE_CARD_CLASS} aria-label="오늘의 운세 카드">
        <img
          src={renderedCardImageUrl ?? FORTUNE_CARD_TEMPLATE_PATH}
          alt="오늘의 운세 결과 카드"
          className="absolute inset-0 z-0 size-full select-none object-contain"
          draggable={false}
        />
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
