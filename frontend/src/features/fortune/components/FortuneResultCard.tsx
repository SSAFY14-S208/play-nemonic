import { CheckCircle2, Home, Pin, Share2, Sparkles, Star } from 'lucide-react'

import { cn } from '@/shared/libs'

import { FORTUNE_SCORE_LABELS } from '../constants'
import { useFortuneSessionStore } from '../fortuneSessionStore'
import { useFortuneExternalShare } from '../hooks'

const POSTIT_SHEET_CLASS = cn(
  'relative isolate w-full',
  'min-h-[min(72dvh,690px)] max-[800px]:min-h-auto',
  'px-[3rem] pt-[1.2rem] pb-[2rem]',
  'max-[800px]:px-4 max-[800px]:pt-[0.95rem] max-[800px]:pb-[1.25rem]',
  'rotate-[-0.55deg] max-[800px]:rotate-0 origin-[50%_16%]',
  'border-[0.12rem] border-[rgba(255,246,212,0.95)]',
  'rounded-[1.05rem_1.2rem_0.9rem_1.1rem] max-[800px]:rounded-[0.95rem]',
  // 배경: solid cream paper color + 그 위에 살짝 그라데이션 액센트.
  // arbitrary property로 background-color와 background-image를 분리해서
  // Tailwind 파서 혼선 없이 카드 안쪽이 항상 불투명 cream으로 깔리도록 보장.
  '[background-color:var(--color-fortune-paper)]',
  '[background-image:radial-gradient(circle_at_16%_14%,rgba(255,255,255,0.92),rgba(255,255,255,0)_17rem),radial-gradient(circle_at_86%_14%,rgba(222,205,255,0.42),rgba(222,205,255,0)_13rem),linear-gradient(180deg,rgba(255,255,255,0.88),rgba(255,248,214,0.98))]',
  'data-[theme=soft-star]:[background-image:radial-gradient(circle_at_18%_14%,rgba(255,255,255,0.94),rgba(255,255,255,0)_17rem),radial-gradient(circle_at_88%_16%,rgba(255,214,232,0.44),rgba(255,214,232,0)_13rem),linear-gradient(180deg,rgba(255,255,255,0.9),rgba(255,248,218,0.98))]',
  'text-fortune-ink',
  '[box-shadow:0_1.9rem_3.8rem_rgba(42,17,60,0.24),0_0.45rem_0_rgba(255,232,139,0.42),inset_0_0.18rem_0_rgba(255,255,255,0.88)]',
  'animate-fortune-postit-land motion-reduce:animate-none',
  // dotted decoration overlay
  "before:content-[''] before:absolute before:inset-[0.45rem] before:z-0 before:rounded-[inherit]",
  'before:bg-[repeating-linear-gradient(0deg,rgba(139,102,177,0.05)_0_1px,rgba(255,255,255,0)_1px_1.8rem),radial-gradient(circle,rgba(138,104,183,0.08)_0_1px,rgba(255,255,255,0)_1.4px)]',
  'before:bg-[length:100%_1.8rem,1.9rem_1.9rem] before:opacity-[0.72] before:pointer-events-none',
  // fold corner triangle
  "after:content-[''] after:absolute after:right-[0.42rem] after:bottom-[0.42rem] after:z-[1]",
  'after:w-[4rem] after:h-[4rem] max-[800px]:after:w-[2.7rem] max-[800px]:after:h-[2.7rem]',
  'after:rounded-[0.25rem_0_0.75rem] after:pointer-events-none',
  'after:bg-[linear-gradient(135deg,rgba(255,255,255,0)_0_49%,rgba(239,217,147,0.52)_50%,rgba(255,246,204,0.94)_100%)]',
  'after:[box-shadow:-0.35rem_-0.35rem_0.75rem_rgba(82,46,96,0.08)]',
)

// 테이프 라벨 — 기존엔 absolute(top:-1.05rem)로 카드 위로 빠져 있었지만,
// 헤더와 자연스러운 흐름으로 묶기 위해 normal flow의 첫 자식으로 배치.
// mx-auto + w-fit 으로 가운데 정렬, rotate는 유지해서 포스트잇 테이프 느낌은 살림.
const POSTIT_TAPE_CLASS = cn(
  'relative z-[5] mx-auto block w-fit',
  'min-w-[10.2rem] max-[800px]:min-w-[8.8rem]',
  'px-[1.8rem] py-[0.62rem] pb-[0.7rem] max-[800px]:px-[1.25rem]',
  'rotate-[-2.4deg]',
  'border-2 border-[rgba(255,255,255,0.55)] rounded-[0.55rem]',
  'bg-[linear-gradient(90deg,rgba(255,255,255,0.24),rgba(255,255,255,0)),rgba(196,174,255,0.86)]',
  'text-[#5d4b78] [font-family:var(--font-fortune-serif)]',
  'text-[1.4rem] max-[800px]:text-[1.15rem] tracking-normal leading-none text-center',
  '[box-shadow:0_0.75rem_1.35rem_rgba(75,42,102,0.16),inset_0_0.12rem_0_rgba(255,255,255,0.6)]',
  'animate-fortune-sticker-pop [animation-delay:180ms] motion-reduce:animate-none',
)

const POPO_STICKER_CLASS = cn(
  'absolute z-[5] pointer-events-none',
  'top-[1.05rem] left-[1.75rem]',
  'max-[800px]:top-[1.15rem] max-[800px]:left-[0.85rem]',
  'inline-flex items-center gap-[0.28rem]',
  'px-[0.82rem] py-[0.54rem] pb-[0.58rem]',
  'rotate-[-9deg]',
  'border-2 border-[rgba(255,255,255,0.84)]',
  'rounded-[999px_999px_999px_0.75rem]',
  'bg-[linear-gradient(180deg,#f4eaff,#dac5ff),#e6d8ff]',
  'text-[#6a4e86] [font-family:var(--font-fortune-serif)]',
  'text-[1.1rem] max-[800px]:text-[0.96rem] tracking-normal',
  '[box-shadow:0_0.65rem_1.2rem_rgba(77,42,104,0.16),inset_0_0.1rem_0_rgba(255,255,255,0.68)]',
  'animate-fortune-sticker-pop [animation-delay:260ms] motion-reduce:animate-none',
)

const STAR_STICKER_BASE = cn(
  'absolute z-[5] pointer-events-none',
  'grid place-items-center',
  'border-2 border-[rgba(255,255,255,0.86)] rounded-full',
  'bg-[linear-gradient(180deg,#fff7c4,#ffd968),#ffe27a]',
  'text-[#8c6a13]',
  '[box-shadow:0_0.65rem_1.2rem_rgba(87,54,15,0.14),inset_0_0.12rem_0_rgba(255,255,255,0.68)]',
  'animate-fortune-sticker-pop motion-reduce:animate-none',
  'max-[800px]:hidden',
)

const STAR_STICKER_LEFT_CLASS = cn(
  STAR_STICKER_BASE,
  'top-[27%] left-[-1.05rem] w-[3.2rem] h-[3.2rem] rotate-[10deg] [animation-delay:340ms]',
)

const STAR_STICKER_RIGHT_CLASS = cn(
  STAR_STICKER_BASE,
  'top-[41%] right-[-0.8rem] w-[2.65rem] h-[2.65rem] rotate-[-12deg] [animation-delay:410ms]',
)

const MOON_STICKER_CLASS = cn(
  'absolute z-[5] pointer-events-none',
  'right-[2.5rem] bottom-[1.75rem]',
  'max-[800px]:right-[0.8rem] max-[800px]:bottom-[0.85rem]',
  'w-[2.9rem] h-[2.9rem] max-[800px]:w-[2.25rem] max-[800px]:h-[2.25rem]',
  'rotate-[14deg]',
  'border-2 border-[rgba(255,255,255,0.82)] rounded-full',
  'bg-[#ffe48a]',
  '[box-shadow:0_0.72rem_1.2rem_rgba(87,54,15,0.14),inset_0_0.12rem_0_rgba(255,255,255,0.64)]',
  'animate-fortune-sticker-pop [animation-delay:480ms] motion-reduce:animate-none',
  // crescent shape via offset white circle
  "after:content-[''] after:absolute after:inset-[0.28rem_0.14rem_0.28rem_0.68rem] after:rounded-full after:bg-[#fff9df]",
)

const SAVE_STAMP_CLASS = cn(
  'absolute z-[5] pointer-events-none',
  'top-[1.18rem] right-[1.75rem]',
  'max-[800px]:top-[1.22rem] max-[800px]:right-[0.82rem]',
  'inline-flex items-center gap-[0.32rem]',
  'px-[0.7rem] py-[0.52rem] pb-[0.55rem]',
  'max-[800px]:px-[0.56rem]',
  'rotate-[5deg]',
  'border-2 border-[rgba(126,91,187,0.62)] rounded-[0.65rem]',
  'bg-[rgba(255,255,255,0.38)]',
  'text-[rgba(111,78,174,0.9)] text-[0.78rem] max-[800px]:text-[0.7rem] font-extrabold leading-none tracking-normal',
  '[box-shadow:inset_0_0_0_0.12rem_rgba(255,255,255,0.32)]',
  'animate-fortune-stamp-press [animation-delay:620ms] motion-reduce:animate-none',
)

const POSTIT_HEADER_TITLE_CLASS = cn(
  'm-0 text-fortune-ink [font-family:var(--font-fortune-serif)]',
  'text-[2.6rem] max-[800px]:text-[1.85rem]',
  'font-normal tracking-normal leading-[1.12] [word-break:keep-all]',
)

const POSTIT_LINE_BLOCK_CLASS = cn(
  'mx-auto mt-7 p-6',
  'max-[800px]:mt-5 max-[800px]:p-[1.05rem_0.85rem]',
  'rotate-[0.45deg]',
  'border-2 border-dashed border-[rgba(151,116,198,0.36)]',
  'rounded-[1rem]',
  'bg-[linear-gradient(180deg,rgba(255,255,255,0.74),rgba(255,244,213,0.82)),rgba(255,250,226,0.86)]',
  '[box-shadow:inset_0_0.18rem_0_rgba(255,255,255,0.56),0_0.8rem_1.5rem_rgba(65,32,87,0.08)]',
  'text-center',
  // .caption-b inside takes muted color
  '[&_.caption-b]:text-fortune-muted',
  // last p inside is the highlighted line
  '[&_p:last-child]:m-0 [&_p:last-child]:mt-[0.45rem]',
  '[&_p:last-child]:text-fortune-accent-strong',
  '[&_p:last-child]:[font-family:var(--font-fortune-serif)]',
  '[&_p:last-child]:text-[2.25rem]',
  '[&_p:last-child]:max-[800px]:text-[1.55rem]',
  '[&_p:last-child]:font-normal [&_p:last-child]:tracking-normal',
  '[&_p:last-child]:leading-[1.16] [&_p:last-child]:[word-break:keep-all]',
)

const POSTIT_SUMMARY_CLASS = cn(
  'max-w-[36rem] mx-auto mt-5',
  'text-[rgba(61,47,73,0.88)]',
  'text-[1.05rem] max-[800px]:text-[0.92rem]',
  'font-semibold tracking-normal leading-[1.68] max-[800px]:leading-[1.58]',
  'text-center [word-break:keep-all]',
)

const SCORE_STICKER_CLASS = cn(
  'relative min-h-[5.7rem] max-[800px]:min-h-[5.1rem]',
  'p-[0.86rem] max-[800px]:p-[0.76rem]',
  'border-2 border-[rgba(255,255,255,0.76)] rounded-[0.78rem]',
  'bg-[linear-gradient(180deg,rgba(255,255,255,0.78),rgba(239,226,255,0.9)),#efe4ff]',
  '[box-shadow:0_0.78rem_1.4rem_rgba(73,42,102,0.1),inset_0_0.12rem_0_rgba(255,255,255,0.72)]',
  'animate-fortune-sticker-pop motion-reduce:animate-none',
  // nth-child variants for rotation + background + animation-delay
  '[&:nth-child(1)]:rotate-[-1.6deg] [&:nth-child(1)]:[animation-delay:220ms]',
  '[&:nth-child(2)]:rotate-[1.4deg] [&:nth-child(2)]:[animation-delay:280ms]',
  '[&:nth-child(2)]:bg-[linear-gradient(180deg,rgba(255,255,255,0.78),rgba(255,228,238,0.9)),#ffe2ec]',
  '[&:nth-child(3)]:rotate-[0.9deg] [&:nth-child(3)]:[animation-delay:340ms]',
  '[&:nth-child(3)]:bg-[linear-gradient(180deg,rgba(255,255,255,0.78),rgba(219,243,236,0.92)),#dff5ee]',
  '[&:nth-child(4)]:rotate-[-1.1deg] [&:nth-child(4)]:[animation-delay:400ms]',
  '[&:nth-child(4)]:bg-[linear-gradient(180deg,rgba(255,255,255,0.78),rgba(255,237,179,0.92)),#ffefbd]',
  // tape strip via ::before
  "before:content-[''] before:absolute before:top-[0.42rem] before:left-1/2 before:-translate-x-1/2",
  'before:w-[1.05rem] before:h-[0.36rem] before:rounded-full before:bg-[rgba(255,255,255,0.62)]',
)

const STICKER_SHARED_BASE = cn(
  'flex items-center min-h-[4.6rem] max-[800px]:min-h-[4.25rem]',
  'px-4 py-[0.9rem] max-[800px]:p-[0.82rem]',
  'border-2 border-[rgba(255,255,255,0.78)] rounded-[0.85rem]',
  'bg-[linear-gradient(180deg,rgba(255,255,255,0.78),rgba(255,245,214,0.9)),#fff1bc]',
  '[box-shadow:0_0.72rem_1.3rem_rgba(73,42,102,0.1),inset_0_0.12rem_0_rgba(255,255,255,0.74)]',
)

const LABEL_STICKER_CLASS = cn(
  STICKER_SHARED_BASE,
  'grid gap-[0.18rem] rotate-[1.2deg]',
  // span: muted caption-like
  '[&>span]:text-fortune-muted [&>span]:text-[0.78rem] [&>span]:font-extrabold [&>span]:tracking-normal',
  // strong: serif accent
  '[&>strong]:block [&>strong]:mt-[0.12rem] [&>strong]:text-fortune-ink',
  '[&>strong]:[font-family:var(--font-fortune-serif)]',
  '[&>strong]:text-[1.3rem]',
  '[&>strong]:font-normal [&>strong]:tracking-normal [&>strong]:leading-[1.05]',
)

const COLOR_STICKER_CLASS = cn(
  STICKER_SHARED_BASE,
  'gap-[0.72rem] rotate-[-1.1deg]',
  // span (not swatch) styled like label
  '[&_span:not([data-fortune-swatch])]:text-fortune-muted',
  '[&_span:not([data-fortune-swatch])]:text-[0.78rem]',
  '[&_span:not([data-fortune-swatch])]:font-extrabold',
  '[&_span:not([data-fortune-swatch])]:tracking-normal',
  '[&_strong]:block [&_strong]:mt-[0.12rem] [&_strong]:text-fortune-ink',
  '[&_strong]:[font-family:var(--font-fortune-serif)]',
  '[&_strong]:text-[1.3rem]',
  '[&_strong]:font-normal [&_strong]:tracking-normal [&_strong]:leading-[1.05]',
)

const COLOR_SWATCH_CLASS = cn(
  'w-[2.35rem] h-[2.35rem] flex-none',
  'border-[0.18rem] border-[rgba(255,255,255,0.88)] rounded-full',
  '[box-shadow:0_0.35rem_0.75rem_rgba(57,36,71,0.12),inset_0_0_0_0.08rem_rgba(69,47,83,0.12)]',
)

const CAUTION_STICKER_CLASS = cn(
  'mt-5 mb-0',
  'px-4 pt-[0.9rem] pb-[0.95rem]',
  'rotate-[-0.45deg]',
  'border-2 border-[rgba(255,255,255,0.78)] rounded-[0.82rem]',
  'bg-[linear-gradient(90deg,rgba(255,255,255,0.62),rgba(235,221,255,0.74)),#f1e6ff]',
  'text-[rgba(61,47,73,0.9)] text-[0.95rem] tracking-normal leading-[1.58]',
  'font-[650] [word-break:keep-all]',
  '[box-shadow:0_0.72rem_1.25rem_rgba(73,42,102,0.1),inset_0_0.12rem_0_rgba(255,255,255,0.7)]',
  // inline label span
  '[&>span]:inline-block [&>span]:mr-[0.5rem]',
  '[&>span]:text-fortune-accent-strong [&>span]:font-black',
)

const SAJU_RECEIPT_CLASS = cn(
  'mt-4 pt-4',
  'border-t-2 border-dashed border-[rgba(151,116,198,0.22)]',
  '[&>summary]:w-fit [&>summary]:cursor-pointer',
  '[&>summary]:text-fortune-accent-strong',
  '[&>summary]:text-[0.9rem] [&>summary]:font-[850] [&>summary]:tracking-normal',
  '[&>p]:mt-[0.5rem] [&>p]:mb-0 [&>p]:text-fortune-muted',
  '[&>p]:text-[0.9rem] [&>p]:leading-[1.55]',
)

const SAJU_PILLAR_LIST_CLASS = cn(
  'grid grid-cols-4 gap-[0.5rem] mt-[0.75rem]',
  '[&>div]:px-[0.45rem] [&>div]:py-[0.55rem]',
  '[&>div]:border [&>div]:border-[rgba(151,116,198,0.18)]',
  '[&>div]:rounded-[var(--radius-md)]',
  '[&>div]:bg-[rgba(255,248,229,0.68)] [&>div]:text-center',
  '[&_dt]:text-fortune-muted [&_dt]:text-[0.72rem] [&_dt]:font-extrabold [&_dt]:tracking-normal',
  '[&_dd]:mt-[0.16rem] [&_dd]:mb-0 [&_dd]:text-fortune-ink',
  '[&_dd]:text-[0.95rem] [&_dd]:font-black',
)

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
    <section className="mx-auto w-[min(92vw,760px)] max-md:w-[min(94vw,560px)] grid gap-5">
      <article className={POSTIT_SHEET_CLASS} data-theme={result.cardTheme} aria-label="오늘의 운세 포스트잇">
        <div className={POPO_STICKER_CLASS} aria-hidden>
          <Sparkles className="size-4" />
          <span>포포</span>
        </div>
        <div className={STAR_STICKER_LEFT_CLASS} aria-hidden>
          <Star className="size-5" />
        </div>
        <div className={STAR_STICKER_RIGHT_CLASS} aria-hidden>
          <Star className="size-4" />
        </div>
        <div className={MOON_STICKER_CLASS} aria-hidden />
        <div className={SAVE_STAMP_CLASS} role="status">
          <CheckCircle2 className="size-4" aria-hidden />
          갤러리 저장
        </div>

        {/* 테이프 + 본문을 한 묶음으로 묶고, 테이프 아래로 20px(=mt-5) 간격을 둡니다.
            relative z-[2]: 카드 ::before 도트 패턴(z-0) 위로 올리되,
            absolute 스티커들(z-[5])보다는 아래 레이어. */}
        <div className="relative z-2 grid">
          <div className={POSTIT_TAPE_CLASS} aria-hidden>
            오늘의 운세
          </div>
          <div className="mt-5">

        <header className="grid gap-[0.35rem] max-w-md mx-auto text-center">
          <p className="caption-b text-fortune-muted">네모닉 운세 메모</p>
          <h1 className={POSTIT_HEADER_TITLE_CLASS}>{result.title}</h1>
        </header>

        <div className={POSTIT_LINE_BLOCK_CLASS}>
          <p className="caption-b">포포의 한 줄</p>
          <p>{result.postitLine}</p>
        </div>

        <p className={POSTIT_SUMMARY_CLASS}>{result.summary}</p>

        <div className="grid grid-cols-4 gap-4 mt-6 max-md:grid-cols-2" aria-label="운세 점수">
            {FORTUNE_SCORE_LABELS.map((scoreLabel) => (
              <div key={scoreLabel.key} className={SCORE_STICKER_CLASS}>
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

        <div className="grid grid-cols-2 gap-4 mt-5">
          <div className={LABEL_STICKER_CLASS}>
            <span>행운 키워드</span>
            <strong>{result.luckyKeyword}</strong>
          </div>
          <div className={COLOR_STICKER_CLASS}>
            <span className={COLOR_SWATCH_CLASS} style={{ background: result.luckyColor.hex }} data-fortune-swatch aria-hidden />
            <div>
              <span>행운 색</span>
              <strong>{result.luckyColor.name}</strong>
            </div>
          </div>
        </div>

        <p className={CAUTION_STICKER_CLASS}>
          <span>주의 메모</span>
          {' '}
          {result.caution}
        </p>

        <details className={SAJU_RECEIPT_CLASS}>
          <summary>입력한 사주 정보</summary>
          <p>{result.sajuSummary}</p>
          <dl className={SAJU_PILLAR_LIST_CLASS}>
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
          </div>
        </div>
      </article>

      <div className="grid gap-3 sm:grid-cols-2">
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
          disabled={!canShareExternal}
          className="body-b flex min-h-12 items-center justify-center gap-2 rounded-[var(--radius-md)] border border-fortune-border bg-fortune-paper px-4 text-fortune-accent-strong shadow-[inset_0_0.08rem_0_rgba(255,255,255,0.72),0_0.65rem_1.35rem_rgba(83,50,102,0.08)] disabled:opacity-50 disabled:cursor-not-allowed"
          onClick={shareExternal}
        >
          <Share2 className="size-4" aria-hidden />
          {isSharingExternal ? '공유 준비 중...' : '외부 공유'}
        </button>
        <button
          type="button"
          className={[
            'body-b relative overflow-hidden flex min-h-12 items-center justify-center gap-2 rounded-[var(--radius-md)] border-0 px-4 text-fortune-inverse',
            // background-color와 background-image를 분리합니다. 한 클래스 안에
            // gradient와 색상 var를 콤마로 같이 적으면 background-image에 색상이
            // 들어왔다며 lightningcss가 빌드를 거부합니다.
            'bg-fortune-accent bg-[linear-gradient(180deg,rgba(255,255,255,0.22),rgba(255,255,255,0))]',
            'shadow-[0_0.8rem_1.7rem_rgba(88,52,129,0.24),0_0_0_0.2rem_rgba(255,234,160,0.2),inset_0_0.1rem_0_rgba(255,255,255,0.3)]',
            "after:content-[''] after:absolute after:inset-0 after:-translate-x-[120%] after:skew-x-[-18deg]",
            'after:bg-[linear-gradient(90deg,rgba(255,255,255,0),rgba(255,255,255,0.35),rgba(255,255,255,0))]',
            'after:transition-transform after:duration-[420ms] after:ease hover:after:translate-x-[120%]',
            'motion-reduce:after:transition-none',
          ].join(' ')}
          onClick={onBackToHub}
        >
          <Home className="size-4" aria-hidden />
          광장으로 돌아가기
        </button>
      </div>
    </section>
  )
}
