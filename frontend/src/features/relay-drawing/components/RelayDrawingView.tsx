'use client'

import dynamic from 'next/dynamic'
import {
  RELAY_COLORS,
  RELAY_DANGER_ACTION,
  RELAY_PARTICIPANTS,
  RELAY_ROUNDS,
  RELAY_TOOLS,
  type RelayToolKey,
} from '../constants'
import type { RelayDrawLine } from '../useRelayDrawing'
import { cn } from '@/shared/libs'
import type { KonvaEventObject } from 'konva/lib/Node'

const RelayDrawingStage = dynamic(() => import('../RelayDrawingStage'), {
  ssr: false,
})

interface RelayDrawingViewProps {
  selectedToolKey: RelayToolKey
  selectedColor: string
  strokeWidth: number
  lines: RelayDrawLine[]
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

export default function RelayDrawingView({
  selectedToolKey,
  selectedColor,
  strokeWidth,
  lines,
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
  return (
    <section className="grid min-h-screen grid-cols-1 bg-relay-background lg:grid-cols-[220px_minmax(0,1fr)_300px]">
      <aside className="order-2 border-relay-border bg-relay-paper p-6 lg:order-none lg:border-r">
        <p className="caption-b text-relay-muted">도구</p>
        <div className="mt-4 grid grid-cols-3 gap-3 lg:grid-cols-3">
          {RELAY_TOOLS.map(({ key, label, Icon, isPrimary }) => {
            const isActive = selectedToolKey === key

            return (
              <button
                key={key}
                type="button"
                aria-label={label}
                onClick={() => {
                  if (key === 'undo') {
                    onUndoDrawing()
                    return
                  }
                  onSelectTool(key)
                }}
                className={cn(
                  'grid size-12 place-items-center rounded-[var(--radius-lg)] border border-relay-border bg-relay-panel text-relay-muted transition-colors',
                  isPrimary && 'bg-relay-active text-relay-ink',
                  isActive && 'border-relay-accent-strong bg-relay-accent text-relay-ink shadow-sm',
                )}
              >
                <Icon className="size-5" aria-hidden />
              </button>
            )
          })}
        </div>

        <div className="mt-7">
          <p className="caption-b text-relay-muted">굵기</p>
          <input
            aria-label="브러시 굵기"
            type="range"
            min={2}
            max={12}
            value={strokeWidth}
            onChange={(event) => onStrokeWidthChange(Number(event.target.value))}
            className="mt-4 w-full accent-relay-accent-strong"
          />
        </div>

        <div className="mt-7">
          <p className="caption-b text-relay-muted">색</p>
          <div className="mt-4 grid grid-cols-4 gap-3">
            {RELAY_COLORS.map((color) => (
              <button
                key={color}
                type="button"
                aria-label={`${color} 색상`}
                onClick={() => onSelectColor(color)}
                className={cn(
                  'size-8 rounded-full border border-relay-border shadow-sm transition-transform hover:scale-105',
                  selectedColor === color && 'ring-2 ring-relay-accent-strong ring-offset-2 ring-offset-relay-paper',
                )}
                style={{ backgroundColor: color }}
              />
            ))}
          </div>
        </div>

        <button
          type="button"
          onClick={onClearDrawing}
          className="caption-b mt-8 inline-flex min-h-10 items-center gap-2 rounded-[var(--radius-md)] bg-relay-panel px-4 text-relay-muted"
        >
          <RELAY_DANGER_ACTION.Icon className="size-4" aria-hidden />
          {RELAY_DANGER_ACTION.label}
        </button>
      </aside>

      <main className="order-1 flex min-h-[680px] items-center justify-center bg-relay-canvas-zone px-6 py-8 lg:order-none">
        <div className="aspect-[848/720] w-full max-w-[848px] overflow-hidden rounded-[var(--radius-xl)] border border-relay-line bg-relay-paper shadow-[0_28px_60px_rgba(148,124,64,0.16)]">
          <RelayDrawingStage
            lines={lines}
            onDrawStart={onDrawStart}
            onDrawMove={onDrawMove}
            onDrawEnd={onDrawEnd}
          />
        </div>
      </main>

      <aside className="order-3 border-relay-border bg-relay-paper p-6 lg:border-l">
        <p className="caption-b text-relay-muted">라운드 진행</p>
        <div className="mt-5 grid gap-4">
          {RELAY_ROUNDS.map((round, index) => {
            const isActive = round.status === 'active'
            const isDone = round.status === 'done'

            return (
              <div
                key={round.key}
                className={cn(
                  'flex min-h-[52px] items-center gap-4 rounded-[var(--radius-lg)] px-3',
                  isActive && 'bg-relay-accent text-relay-ink',
                  !isActive && 'bg-relay-panel text-relay-muted',
                )}
              >
                <span
                  className={cn(
                    'grid size-7 place-items-center rounded-full body-b',
                    isDone && 'bg-relay-accent-strong text-fg-inverse',
                    isActive && 'bg-relay-panel text-relay-accent-strong',
                    !isDone && !isActive && 'bg-relay-disabled text-fg-inverse',
                  )}
                >
                  {isDone ? '✓' : index + 1}
                </span>
                <span className="body-b">{round.label}</span>
              </div>
            )
          })}
        </div>

        <div className="mt-8 border-t border-relay-border pt-6">
          <p className="caption-b text-relay-muted">함께하는 친구들</p>
          <div className="mt-4 grid gap-3">
            {RELAY_PARTICIPANTS.map((participant) => {
              const ParticipantIcon = participant.Icon

              return (
                <div
                  key={participant.id}
                  className="inline-flex min-h-11 w-fit items-center gap-3 rounded-[var(--radius-lg)] bg-relay-active px-3 text-relay-ink"
                >
                  <span className="grid size-7 place-items-center rounded-full bg-relay-panel text-relay-accent-strong">
                    <ParticipantIcon className="size-4" aria-hidden />
                  </span>
                  <span className="caption-b">{participant.name}</span>
                </div>
              )
            })}
          </div>
        </div>

        <div className="mt-6 rounded-[var(--radius-lg)] bg-relay-accent p-4 text-relay-ink">
          <p className="caption-b">💡 팁</p>
          <p className="caption-r mt-2">
            이전 사람 그림의 하단 일부만 보여요. 마음껏 상상해서 이어 그려보세요!
          </p>
        </div>

        <button
          type="button"
          onClick={onCompleteRound}
          className="body-b mt-6 min-h-12 w-full rounded-[var(--radius-lg)] border border-relay-accent-strong bg-relay-accent text-relay-ink shadow-sm"
        >
          다음 사람에게 넘기기 →
        </button>
      </aside>
    </section>
  )
}
