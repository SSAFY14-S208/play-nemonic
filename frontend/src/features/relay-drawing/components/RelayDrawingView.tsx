'use client'

import dynamic from 'next/dynamic'
import {
  RELAY_COLORS,
  RELAY_ROUND_ORDER,
  RELAY_ROUNDS,
  RELAY_ROUND_SEGMENTS,
  type RelayRoundKey,
  type RelayToolKey,
} from '../constants'
import type { RelayDrawLine, RelayRoundLines } from '../useRelayDrawing'
import { cn } from '@/shared/libs'
import type { KonvaEventObject } from 'konva/lib/Node'

const RelayDrawingStage = dynamic(() => import('../RelayDrawingStage'), {
  ssr: false,
})

interface RelayDrawingViewProps {
  activeRoundKey: RelayRoundKey
  activeRoundIndex: number
  selectedToolKey: RelayToolKey
  selectedColor: string
  strokeWidth: number
  lines: RelayDrawLine[]
  previousRoundLines: RelayDrawLine[]
  roundLines: RelayRoundLines
  onSelectTool: (tool: RelayToolKey) => void
  onSelectColor: (color: string) => void
  onStrokeWidthChange: (strokeWidth: number) => void
  onUndoDrawing: () => void
  onClearDrawing: () => void
  onDrawStart: (event: KonvaEventObject<MouseEvent | TouchEvent>) => void
  onDrawMove: (event: KonvaEventObject<MouseEvent | TouchEvent>) => void
  onDrawEnd: () => void
  onCompleteRound: () => void
}

const DRAWING_TOOL_BUTTONS = [
  { key: 'pencil', label: '연필', icon: '✏️', action: 'select' },
  { key: 'eraser', label: '지우개', icon: '🧽', action: 'select' },
  { key: 'bucket', label: '채우기', icon: '🪣', action: 'select' },
  { key: 'undo', label: '되돌리기', icon: '↩️', action: 'undo' },
  { key: 'redo', label: '다시 실행', icon: '↪️', action: 'noop' },
  { key: 'clear', label: '비우기', icon: '🗑️', action: 'clear' },
] as const

const STROKE_WIDTH_OPTIONS = [3, 6, 10]
const DRAWING_PARTICIPANTS = [
  { id: 'fox', avatar: '🦊', name: '여우 (나)' },
  { id: 'cat', avatar: '🐱', name: '고양이' },
  { id: 'bear', avatar: '🐻', name: '곰돌이' },
]

export default function RelayDrawingView({
  activeRoundKey,
  activeRoundIndex,
  selectedToolKey,
  selectedColor,
  strokeWidth,
  lines,
  previousRoundLines,
  roundLines,
  onSelectTool,
  onSelectColor,
  onStrokeWidthChange,
  onUndoDrawing,
  onClearDrawing,
  onDrawStart,
  onDrawMove,
  onDrawEnd,
  onCompleteRound,
}: RelayDrawingViewProps) {
  const activeRound = RELAY_ROUND_SEGMENTS[activeRoundKey]
  const isLastRound = activeRoundIndex === RELAY_ROUND_ORDER.length - 1

  return (
    <section className="relative min-h-[900px] overflow-hidden bg-relay-background text-relay-ink">
      <div className="relative mx-auto h-[900px] w-full max-w-[1440px] overflow-hidden">
        <aside className="absolute left-[33px] top-[184px] flex h-[481px] w-[225px] flex-col justify-center gap-4 rounded-[24px] bg-relay-paper p-6 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
          <DrawingToolPanel
            selectedToolKey={selectedToolKey}
            selectedColor={selectedColor}
            strokeWidth={strokeWidth}
            onSelectTool={onSelectTool}
            onSelectColor={onSelectColor}
            onStrokeWidthChange={onStrokeWidthChange}
            onUndoDrawing={onUndoDrawing}
            onClearDrawing={onClearDrawing}
          />
        </aside>

        <main className="absolute left-[273px] top-[90px] h-[720px] w-[848px] overflow-hidden rounded-[16px] bg-relay-paper shadow-[0_28px_60px_rgba(148,124,64,0.12)]">
          <RelayDrawingStage
            activeRoundKey={activeRoundKey}
            lines={lines}
            previousRoundLines={previousRoundLines}
            onDrawStart={onDrawStart}
            onDrawMove={onDrawMove}
            onDrawEnd={onDrawEnd}
          />
        </main>

        <aside className="absolute left-[1136px] top-[143px] h-[625px] w-[270px] overflow-hidden rounded-[24px] bg-relay-paper p-6 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
          <RoundProgressPanel
            activeRoundKey={activeRoundKey}
            activeRoundIndex={activeRoundIndex}
            roundLines={roundLines}
          />
        </aside>

        <button
          type="button"
          onClick={onCompleteRound}
          className="body-b absolute left-[1136px] top-[792px] min-h-14 w-[270px] rounded-[16px] bg-relay-accent text-relay-ink shadow-[0_6px_16px_rgba(184,121,22,0.35)]"
        >
          {isLastRound ? '결과 합치기 →' : `${activeRound.label} 저장하고 다음 →`}
        </button>
      </div>
    </section>
  )
}

function DrawingToolPanel({
  selectedToolKey,
  selectedColor,
  strokeWidth,
  onSelectTool,
  onSelectColor,
  onStrokeWidthChange,
  onUndoDrawing,
  onClearDrawing,
}: {
  selectedToolKey: RelayToolKey
  selectedColor: string
  strokeWidth: number
  onSelectTool: (tool: RelayToolKey) => void
  onSelectColor: (color: string) => void
  onStrokeWidthChange: (strokeWidth: number) => void
  onUndoDrawing: () => void
  onClearDrawing: () => void
}) {
  return (
    <>
      <section className="grid gap-4">
        <p className="h4-b text-relay-ink">도구</p>
        <div className="grid grid-cols-3 gap-2">
          {DRAWING_TOOL_BUTTONS.map((tool) => {
            const isSelectableTool = tool.action === 'select'
            const isActive = isSelectableTool && selectedToolKey === tool.key

            return (
              <button
                key={tool.key}
                type="button"
                aria-label={tool.label}
                onClick={() => {
                  if (tool.action === 'undo') {
                    onUndoDrawing()
                    return
                  }
                  if (tool.action === 'clear') {
                    onClearDrawing()
                    return
                  }
                  if (tool.action === 'noop') {
                    return
                  }
                  onSelectTool(tool.key)
                }}
                className={cn(
                  'grid size-[50px] place-items-center rounded-[14px] border border-relay-line bg-relay-active text-[22px]',
                  isActive && 'border-relay-accent-strong bg-relay-accent shadow-sm',
                )}
              >
                {tool.icon}
              </button>
            )
          })}
        </div>
      </section>

      <section className="grid gap-4">
        <p className="h4-b text-relay-ink">굵기</p>
        <div className="grid grid-cols-3 gap-2">
          {STROKE_WIDTH_OPTIONS.map((strokeWidthOption) => (
            <button
              key={strokeWidthOption}
              type="button"
              aria-label={`${strokeWidthOption}px 굵기`}
              onClick={() => onStrokeWidthChange(strokeWidthOption)}
              className={cn(
                'grid min-h-8 place-items-center rounded-[12px] border border-relay-line bg-relay-active',
                strokeWidth === strokeWidthOption && 'border-2 border-relay-line bg-relay-accent',
              )}
            >
              <span
                className="rounded-full bg-relay-dash"
                style={{
                  width: `${strokeWidthOption}px`,
                  height: `${strokeWidthOption}px`,
                }}
              />
            </button>
          ))}
        </div>
      </section>

      <section className="grid gap-4">
        <p className="h4-b text-relay-ink">색</p>
        <div className="flex flex-wrap gap-2">
          {RELAY_COLORS.map((color) => (
            <button
              key={color}
              type="button"
              aria-label={`${color} 색상`}
              onClick={() => onSelectColor(color)}
              className={cn(
                'size-9 rounded-full border border-transparent',
                selectedColor === color && 'border-[3px] border-relay-accent-strong',
                color === '#ffffff' && 'border-relay-dash',
              )}
              style={{ backgroundColor: color }}
            />
          ))}
        </div>
      </section>
    </>
  )
}

function RoundProgressPanel({
  activeRoundKey,
  activeRoundIndex,
  roundLines,
}: {
  activeRoundKey: RelayRoundKey
  activeRoundIndex: number
  roundLines: RelayRoundLines
}) {
  return (
    <div className="flex h-full flex-col gap-4">
      <p className="h4-b text-relay-ink">라운드 진행</p>
      <div className="relative h-[188px] shrink-0">
        {RELAY_ROUNDS.map((round, roundIndex) => {
          const isDone = roundIndex < activeRoundIndex
          const isActive = round.key === activeRoundKey
          const hasDrawing = roundLines[round.key].length > 0

          return (
            <div
              key={round.key}
              className={cn(
                'absolute left-0 flex h-[52px] w-full items-center rounded-[14px] px-5',
                isActive && 'border-2 border-relay-line bg-relay-accent text-relay-ink',
                !isActive && 'bg-relay-panel text-relay-muted',
                roundIndex > activeRoundIndex && 'opacity-60',
              )}
              style={{ top: `${roundIndex * 68}px` }}
            >
              <span
                className={cn(
                  'body-b grid size-7 place-items-center rounded-full',
                  isDone && 'bg-relay-accent-strong text-fg-inverse',
                  isActive && 'bg-relay-active text-relay-ink',
                  !isDone && !isActive && 'bg-relay-disabled text-fg-inverse',
                )}
              >
                {isDone ? '✓' : roundIndex + 1}
              </span>
              <span className="body-b flex-1 text-center">{round.label}</span>
              {isActive && <span className="size-2.5 rounded-full bg-relay-accent-strong" />}
              {!isActive && hasDrawing && <span className="size-2.5 rounded-full bg-relay-green" />}
            </div>
          )
        })}
      </div>

      <div className="h-px w-full bg-relay-accent" />
      <p className="h4-b text-relay-ink">함께하는 친구들</p>
      <div className="grid gap-4">
        {DRAWING_PARTICIPANTS.map((participant) => (
          <div
            key={participant.id}
            className="flex min-h-12 items-center gap-3 rounded-[12px] border border-relay-line bg-relay-active px-3 text-relay-accent-strong"
          >
            <span className="grid size-7 place-items-center rounded-full border border-relay-line bg-relay-active">
              {participant.avatar}
            </span>
            <span className="body-b">{participant.name}</span>
          </div>
        ))}
      </div>

      <div className="caption-r mt-auto rounded-[14px] bg-relay-accent px-4 py-3 text-relay-ink">
        <p className="caption-b">💡 팁</p>
        <p className="mt-1">
          이전 사람 그림의 하단 일부만 보여요. 마음껏 상상해서 이어 그려보세요!
        </p>
      </div>
    </div>
  )
}
