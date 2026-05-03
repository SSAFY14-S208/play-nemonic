import {
  RELAY_RESULT_ACTIONS,
  RELAY_RESULT_REVEALS,
  type RelayResultReveal,
  type RelayResultRevealStep,
} from '../constants'
import { cn } from '@/shared/libs'

interface RelayResultViewProps {
  resultRevealStep: RelayResultRevealStep
  canShowPreviousResultReveal: boolean
  canShowNextResultReveal: boolean
  onShowPreviousResultReveal: () => void
  onShowNextResultReveal: () => void
  onCreateAnother: () => void
}

const RESULT_SEGMENTS = [
  {
    key: 'face',
    avatar: '🐱',
    participantName: '고양이',
    roleLabel: '얼굴',
    tagLabel: '🐱 고양이 · 얼굴',
    tagClassName: 'bg-relay-active',
  },
  {
    key: 'body',
    avatar: '🦊',
    participantName: '여우 (나)',
    roleLabel: '몸통',
    tagLabel: '🦊 여우 · 몸통',
    tagClassName: 'bg-relay-segment-body',
  },
  {
    key: 'legs',
    avatar: '🐻',
    participantName: '곰돌이',
    roleLabel: '다리',
    tagLabel: '🐻 곰돌이 · 다리',
    tagClassName: 'bg-relay-segment-legs',
  },
] as const

export default function RelayResultView({
  resultRevealStep,
  canShowPreviousResultReveal,
  canShowNextResultReveal,
  onShowPreviousResultReveal,
  onShowNextResultReveal,
  onCreateAnother,
}: RelayResultViewProps) {
  const activeReveal =
    RELAY_RESULT_REVEALS.find((reveal) => reveal.key === resultRevealStep) ??
    RELAY_RESULT_REVEALS[0]
  const activeRevealIndex = RELAY_RESULT_REVEALS.findIndex(
    (reveal) => reveal.key === activeReveal.key,
  )
  const isFinalReveal = activeReveal.key === 'final'

  return (
    <section className="min-h-screen bg-relay-background px-6 py-10 text-relay-ink lg:px-12 lg:py-14">
      <div className="mx-auto w-full max-w-[1312px]">
        <ResultProgressStrip activeReveal={activeReveal} activeRevealIndex={activeRevealIndex} />

        <div className="mt-5 grid gap-8 lg:grid-cols-[minmax(0,880px)_400px] lg:items-start">
          <section
            className={cn(
              'overflow-hidden rounded-[18px] bg-relay-paper px-5 py-6 shadow-[0_14px_28px_rgba(212,156,31,0.12)] md:px-7',
              isFinalReveal ? 'min-h-[687px]' : 'min-h-[716px]',
            )}
          >
            {!isFinalReveal && <ResultStageHeader activeReveal={activeReveal} />}

            <ResultCanvas resultRevealStep={activeReveal.key} />

            {!isFinalReveal && (
              <ResultStepNav
                activeReveal={activeReveal}
                activeRevealIndex={activeRevealIndex}
                canShowPreviousResultReveal={canShowPreviousResultReveal}
                canShowNextResultReveal={canShowNextResultReveal}
                onShowPreviousResultReveal={onShowPreviousResultReveal}
                onShowNextResultReveal={onShowNextResultReveal}
              />
            )}
          </section>

          <aside className="flex min-h-[716px] flex-col gap-4">
            <ResultCreditsPanel
              activeReveal={activeReveal}
              activeRevealIndex={activeRevealIndex}
              isFinalReveal={isFinalReveal}
            />
            <ResultAlbumsPanel />
            <div className="hidden flex-1 lg:block" />

            {isFinalReveal && (
              <>
                <div className="grid min-h-[60px] gap-3 sm:grid-cols-2">
                  {RELAY_RESULT_ACTIONS.map(({ label, Icon }, index) => (
                    <button
                      key={label}
                      type="button"
                      className={cn(
                        'body-b inline-flex items-center justify-center gap-2 rounded-[14px] border-[1.5px] px-5',
                        index === 0 &&
                          'border-relay-line bg-relay-paper text-relay-ink',
                        index !== 0 &&
                          'border-relay-accent bg-relay-accent text-relay-ink shadow-[0_4px_10px_rgba(212,156,31,0.18)]',
                      )}
                    >
                      <Icon className="size-4" aria-hidden />
                      {label}
                    </button>
                  ))}
                </div>

                <button
                  type="button"
                  onClick={onCreateAnother}
                  className="caption-b self-center text-relay-muted"
                >
                  새 릴레이 만들기
                </button>
              </>
            )}
          </aside>
        </div>
      </div>
    </section>
  )
}

function ResultProgressStrip({
  activeReveal,
  activeRevealIndex,
}: {
  activeReveal: RelayResultReveal
  activeRevealIndex: number
}) {
  return (
    <div className="flex min-h-9 items-center justify-between gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="caption-b text-relay-accent-strong">2026.04.28 ·</span>
        <span className="caption-b rounded-full bg-relay-active px-3 py-1 text-relay-ink">
          🐱 고양이 님의 앨범
        </span>
      </div>

      <div className="flex items-center gap-1.5">
        {RELAY_RESULT_REVEALS.map((reveal, revealIndex) => (
          <span
            key={reveal.key}
            className={cn(
              'size-[9px] rounded-full bg-relay-line',
              revealIndex <= activeRevealIndex && 'bg-relay-accent-strong',
            )}
          />
        ))}
        <span className="caption-b ml-1 text-relay-accent-strong">
          {activeReveal.order} / {RELAY_RESULT_REVEALS.length}
        </span>
      </div>
    </div>
  )
}

function ResultStageHeader({ activeReveal }: { activeReveal: RelayResultReveal }) {
  return (
    <header className="mb-4 flex min-h-[60px] items-end justify-between gap-4">
      <div>
        <p className="caption-b text-relay-accent-strong">
          STEP {activeReveal.order} · {activeReveal.roleLabel}
        </p>
        <h1 className="h2-b mt-1 flex flex-wrap items-center gap-2 text-relay-ink">
          <span className="rounded-full bg-relay-active px-3 py-0.5">
            {activeReveal.avatar} {activeReveal.participantName}
          </span>
          <span>{activeReveal.titleSuffix}</span>
        </h1>
      </div>

      <span className="caption-b rounded-full border-[1.5px] border-relay-line bg-relay-paper px-3 py-1 text-relay-accent-strong">
        {activeReveal.avatar} {activeReveal.roleLabel}
      </span>
    </header>
  )
}

function ResultCanvas({ resultRevealStep }: { resultRevealStep: RelayResultRevealStep }) {
  const isFinalReveal = resultRevealStep === 'final'

  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-[14px] border-[1.5px] border-relay-line bg-relay-background',
        isFinalReveal ? 'h-[495px]' : 'h-[460px]',
      )}
    >
      {resultRevealStep === 'face' && (
        <>
          <FaceDrawing className="absolute left-[8%] top-[11%] h-[88%] w-[84%]" />
          <ResultSpotlight revealStep="face" />
        </>
      )}

      {resultRevealStep === 'body' && (
        <>
          <FaceDrawing className="absolute left-[20%] top-[-56%] h-[88%] w-[60%]" />
          <BodyDrawing className="absolute left-1/2 top-[11%] h-[76%] w-[56%] -translate-x-1/2" />
          <ResultSpotlight revealStep="body" />
        </>
      )}

      {resultRevealStep === 'legs' && (
        <>
          <BodyDrawing className="absolute left-1/2 top-[-55%] h-[76%] w-[54%] -translate-x-1/2" />
          <LegsDrawing className="absolute left-1/2 top-[1%] h-[94%] w-[48%] -translate-x-1/2" />
          <ResultSpotlight revealStep="legs" />
        </>
      )}

      {isFinalReveal && (
        <>
          <FaceDrawing className="absolute left-1/2 top-[4%] h-[34%] w-[38%] -translate-x-1/2" />
          <BodyDrawing className="absolute left-1/2 top-[27%] h-[31%] w-[28%] -translate-x-1/2" />
          <LegsDrawing className="absolute left-1/2 top-[54%] h-[37%] w-[24%] -translate-x-1/2" />
          <SegmentGuide top="24%" />
          <SegmentGuide top="53%" />
          <ResultSegmentTags />
        </>
      )}
    </div>
  )
}

function ResultSpotlight({ revealStep }: { revealStep: RelayResultRevealStep }) {
  const activeReveal =
    RELAY_RESULT_REVEALS.find((reveal) => reveal.key === revealStep) ?? RELAY_RESULT_REVEALS[0]

  return (
    <div className="absolute right-4 top-4 flex items-center gap-2 rounded-full border-[1.5px] border-relay-line bg-relay-paper py-1.5 pl-2 pr-4 shadow-[0_6px_7px_rgba(212,156,31,0.18)]">
      <span className="grid size-8 place-items-center rounded-full bg-relay-active">
        {activeReveal.avatar}
      </span>
      <span>
        <span className="caption-b block text-relay-accent-strong">
          {activeReveal.spotlightLabel}
        </span>
        <span className="caption-b block text-relay-ink">{activeReveal.participantName}</span>
      </span>
    </div>
  )
}

function ResultStepNav({
  activeReveal,
  activeRevealIndex,
  canShowPreviousResultReveal,
  canShowNextResultReveal,
  onShowPreviousResultReveal,
  onShowNextResultReveal,
}: {
  activeReveal: RelayResultReveal
  activeRevealIndex: number
  canShowPreviousResultReveal: boolean
  canShowNextResultReveal: boolean
  onShowPreviousResultReveal: () => void
  onShowNextResultReveal: () => void
}) {
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

function ResultCreditsPanel({
  activeReveal,
  activeRevealIndex,
  isFinalReveal,
}: {
  activeReveal: RelayResultReveal
  activeRevealIndex: number
  isFinalReveal: boolean
}) {
  return (
    <section className="rounded-[18px] border border-relay-line bg-relay-paper px-5 py-4">
      <p className="caption-b text-relay-accent-strong">이번엔 3명이 모였어요</p>
      <h2 className="h4-b mt-2 text-relay-ink">🐱 고양이 님의 캐릭터</h2>

      <div className="mt-4 grid gap-2">
        {RESULT_SEGMENTS.map((segment, segmentIndex) => {
          const isActive = !isFinalReveal && segment.roleLabel === activeReveal.roleLabel
          const isComplete = isFinalReveal || segmentIndex < activeRevealIndex

          return (
            <div
              key={segment.key}
              className={cn(
                'flex min-h-11 items-center gap-3 rounded-[14px] bg-relay-credit-row px-3.5',
                isActive &&
                  'border-[1.5px] border-relay-accent-strong bg-relay-paper shadow-[0_4px_5px_rgba(212,155,31,0.18)]',
              )}
            >
              <span className="text-[18px]">{segment.avatar}</span>
              <span className="body-b text-relay-ink">{segment.participantName}</span>
              <span className="flex-1" />
              <span
                className={cn(
                  'caption-b rounded-full border border-relay-line bg-relay-paper px-2.5 py-1 text-relay-accent-strong',
                  isActive && 'border-relay-accent-strong bg-relay-active',
                )}
              >
                {segment.roleLabel}
              </span>
              {isComplete && (
                <span className="caption-b grid size-[18px] place-items-center rounded-full bg-relay-accent-strong text-fg-inverse">
                  ✓
                </span>
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}

function ResultAlbumsPanel() {
  return (
    <section className="rounded-[18px] border border-relay-line bg-relay-paper p-5">
      <p className="caption-b text-relay-accent-strong">앨범 둘러보기</p>
      <div className="mt-3 grid grid-cols-3 gap-2">
        {RESULT_SEGMENTS.map((segment, index) => (
          <button
            key={segment.key}
            type="button"
            className={cn(
              'caption-b grid min-h-[88px] place-items-center rounded-[12px] border-[1.5px] border-relay-line bg-relay-credit-row text-relay-accent-strong',
              index === 0 && 'border-relay-accent-strong bg-relay-active',
            )}
          >
            <span className="grid justify-items-center gap-1">
              <span className="text-[22px]">{segment.avatar}</span>
              <span>{segment.participantName.replace(' (나)', '')}</span>
            </span>
          </button>
        ))}
      </div>
    </section>
  )
}

function ResultSegmentTags() {
  return (
    <>
      {RESULT_SEGMENTS.map((segment, index) => (
        <span
          key={segment.key}
          className={cn(
            'caption-b absolute left-4 rounded-full px-3 py-1 text-relay-ink',
            segment.tagClassName,
          )}
          style={{ top: `${3 + index * 29}%` }}
        >
          {segment.tagLabel}
        </span>
      ))}
    </>
  )
}

function SegmentGuide({ top }: { top: string }) {
  return (
    <span
      className="absolute left-4 right-4 border-t border-dashed border-relay-accent-strong/45"
      style={{ top }}
    />
  )
}

function FaceDrawing({ className }: { className: string }) {
  return (
    <div className={cn('relative', className)}>
      <span className="absolute left-1/2 top-[5%] h-[7%] w-[28%] -translate-x-1/2 rounded-full border-[3px] border-relay-ink bg-transparent" />
      <div className="absolute left-1/2 top-[10%] h-[76%] w-[48%] -translate-x-1/2 rounded-full border-[4px] border-relay-ink bg-relay-yellow">
        <span className="body-b absolute left-[32%] top-[46%] -translate-x-1/2 text-relay-ink">
          X
        </span>
        <span className="body-b absolute right-[32%] top-[46%] translate-x-1/2 text-relay-ink">
          X
        </span>
        <span className="absolute bottom-[24%] left-1/2 h-[8%] w-[15%] -translate-x-1/2 rounded-full border-[2px] border-relay-ink bg-relay-paper" />
      </div>
    </div>
  )
}

function BodyDrawing({ className }: { className: string }) {
  return (
    <div className={cn('relative', className)}>
      <span className="absolute left-[14%] top-[24%] h-[2px] w-[28%] -rotate-[36deg] rounded-full bg-relay-coral" />
      <span className="absolute right-[14%] top-[24%] h-[2px] w-[28%] rotate-[36deg] rounded-full bg-relay-coral" />
      <div className="absolute left-1/2 top-[12%] h-[76%] w-[34%] -translate-x-1/2 border-[3px] border-relay-coral bg-transparent">
        {[30, 50, 70].map((verticalPosition) => (
          <span
            key={verticalPosition}
            className="absolute left-1/2 size-3 -translate-x-1/2 rounded-full bg-relay-coral"
            style={{ top: `${verticalPosition}%` }}
          />
        ))}
      </div>
    </div>
  )
}

function LegsDrawing({ className }: { className: string }) {
  return (
    <div className={cn('relative', className)}>
      <span className="absolute bottom-[12%] left-[36%] h-[78%] w-[5px] rotate-[5deg] rounded-full bg-relay-green" />
      <span className="absolute bottom-[12%] right-[36%] h-[78%] w-[5px] rotate-[-5deg] rounded-full bg-relay-green" />
      <span className="absolute bottom-[7%] left-[22%] h-[5%] w-[24%] rounded-full bg-relay-ink" />
      <span className="absolute bottom-[7%] right-[22%] h-[5%] w-[24%] rounded-full bg-relay-ink" />
    </div>
  )
}
