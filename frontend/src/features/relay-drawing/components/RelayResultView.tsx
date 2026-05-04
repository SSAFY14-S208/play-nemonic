import {
  RELAY_FINAL_STAGE_SIZE,
  RELAY_RESULT_ACTIONS,
  RELAY_RESULT_REVEALS,
  RELAY_ROUND_ORDER,
  RELAY_ROUND_RULES,
  RELAY_STAGE_SIZE,
  type RelayResultReveal,
  type RelayResultRevealStep,
  type RelayRoundKey,
} from '../constants'
import type { RelayDrawLine, RelayRoundLines } from '../useRelayDrawing'
import { cn } from '@/shared/libs'

interface RelayResultViewProps {
  resultRevealStep: RelayResultRevealStep
  roundLines: RelayRoundLines
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
  roundLines,
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

            <ResultCanvas resultRevealStep={activeReveal.key} roundLines={roundLines} />

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

function ResultCanvas({
  resultRevealStep,
  roundLines,
}: {
  resultRevealStep: RelayResultRevealStep
  roundLines: RelayRoundLines
}) {
  const isFinalReveal = resultRevealStep === 'final'
  const visibleRoundKeys =
    resultRevealStep === 'final' ? RELAY_ROUND_ORDER : [resultRevealStep as RelayRoundKey]

  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-[14px] border-[1.5px] border-relay-line bg-relay-background',
        isFinalReveal ? 'h-[495px]' : 'h-[460px]',
      )}
    >
      <CompositeDrawingCanvas
        visibleRoundKeys={visibleRoundKeys}
        roundLines={roundLines}
        isFinalReveal={isFinalReveal}
      />

      {!isFinalReveal && (
        <ResultSpotlight revealStep={resultRevealStep} />
      )}

      {isFinalReveal && <ResultSegmentTags />}
    </div>
  )
}

function CompositeDrawingCanvas({
  visibleRoundKeys,
  roundLines,
  isFinalReveal,
}: {
  visibleRoundKeys: RelayRoundKey[]
  roundLines: RelayRoundLines
  isFinalReveal: boolean
}) {
  const hasVisibleLines = visibleRoundKeys.some((roundKey) => roundLines[roundKey].length > 0)
  const viewBoxHeight = isFinalReveal ? RELAY_FINAL_STAGE_SIZE.height : RELAY_STAGE_SIZE.height
  const dotRowCount = Math.ceil(viewBoxHeight / 20)
  const separatorPositions = RELAY_ROUND_ORDER.slice(1).map(
    (roundKey) => RELAY_ROUND_RULES[roundKey].finalOffsetY + RELAY_ROUND_RULES[roundKey].drawArea.y,
  )

  return (
    <svg
      className="h-full w-full"
      viewBox={`0 0 ${RELAY_STAGE_SIZE.width} ${viewBoxHeight}`}
      role="img"
      aria-label="완성된 릴레이 드로잉"
      preserveAspectRatio="xMidYMid meet"
    >
      <rect width={RELAY_STAGE_SIZE.width} height={viewBoxHeight} fill="#fffdf7" />
      {Array.from({ length: dotRowCount }).map((_, rowIndex) =>
        Array.from({ length: 42 }).map((__, columnIndex) => (
          <circle
            key={`${rowIndex}-${columnIndex}`}
            cx={12 + columnIndex * 20}
            cy={12 + rowIndex * 20}
            r={1}
            fill="#ffdc82"
            opacity={0.54}
          />
        )),
      )}

      {isFinalReveal &&
        separatorPositions.map((separatorPosition) => (
          <line
            key={separatorPosition}
            x1={16}
            x2={RELAY_STAGE_SIZE.width - 16}
            y1={separatorPosition}
            y2={separatorPosition}
            stroke="#d49b1f"
            strokeDasharray="7 9"
            opacity={0.45}
          />
        ))}

      {visibleRoundKeys.map((roundKey) => (
        <RoundLineGroup
          key={roundKey}
          roundKey={roundKey}
          lines={roundLines[roundKey]}
          isFinalReveal={isFinalReveal}
        />
      ))}

      {!hasVisibleLines && (
        <text
          x={RELAY_STAGE_SIZE.width / 2}
          y={viewBoxHeight / 2}
          textAnchor="middle"
          dominantBaseline="middle"
          fill="#947c40"
          fontFamily="Pretendard Variable"
          fontSize={18}
          fontWeight={700}
        >
          아직 저장된 그림이 없어요
        </text>
      )}
    </svg>
  )
}

function RoundLineGroup({
  roundKey,
  lines,
  isFinalReveal,
}: {
  roundKey: RelayRoundKey
  lines: RelayDrawLine[]
  isFinalReveal: boolean
}) {
  const roundRule = RELAY_ROUND_RULES[roundKey]
  const verticalOffset = isFinalReveal ? roundRule.finalOffsetY : 0
  const clipId = `relay-result-${roundKey}-${isFinalReveal ? 'final' : 'single'}`

  return (
    <>
      <defs>
        <clipPath id={clipId}>
          <rect
            x={0}
            y={roundRule.exportArea.y}
            width={RELAY_STAGE_SIZE.width}
            height={roundRule.exportArea.height}
          />
        </clipPath>
      </defs>
      <g clipPath={`url(#${clipId})`} transform={`translate(0 ${verticalOffset})`}>
        {lines.map((line) => {
          if (line.kind === 'fill') {
            if (line.imageDataUrl) {
              return (
                <image
                  key={line.id}
                  href={line.imageDataUrl}
                  x={0}
                  y={0}
                  width={RELAY_STAGE_SIZE.width}
                  height={RELAY_STAGE_SIZE.height}
                />
              )
            }

            return (
              <polygon
                key={line.id}
                points={line.points.map((point) => `${point.x},${point.y}`).join(' ')}
                fill={line.color}
              />
            )
          }

          return (
            <polyline
              key={line.id}
              points={line.points.map((point) => `${point.x},${point.y}`).join(' ')}
              fill="none"
              stroke={line.color}
              strokeWidth={line.strokeWidth}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          )
        })}
      </g>
    </>
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
