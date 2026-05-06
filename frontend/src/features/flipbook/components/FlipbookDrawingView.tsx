'use client'

import dynamic from 'next/dynamic'
import { DrawingSessionControls, DrawingToolPanel } from '@/shared/components'
import type { DrawingLine, DrawingPointerEvent, DrawingToolKey } from '@/shared/types'
import {
  FLIPBOOK_COLORS,
  FLIPBOOK_PARTICIPANTS,
  type FlipbookParticipant,
} from '../constants'

const FlipbookStage = dynamic(() => import('../FlipbookStage'), {
  ssr: false,
})

interface FlipbookDrawingViewProps {
  activeRoundIndex: number
  roundCount: number
  remainingSeconds: number
  currentParticipant: FlipbookParticipant
  lines: DrawingLine[]
  previousFrameLines: DrawingLine[]
  selectedToolKey: DrawingToolKey
  selectedColor: string
  strokeWidth: number
  onSelectTool: (toolKey: DrawingToolKey) => void
  onSelectColor: (color: string) => void
  onStrokeWidthChange: (strokeWidth: number) => void
  onUndoDrawing: () => void
  onRedoDrawing: () => void
  onClearDrawing: () => void
  onDrawStart: (event: DrawingPointerEvent) => void
  onDrawMove: (event: DrawingPointerEvent) => void
  onDrawEnd: () => void
  onExit: () => void
  onCompleteRound: () => void
}

export default function FlipbookDrawingView({
  activeRoundIndex,
  roundCount,
  remainingSeconds,
  currentParticipant,
  lines,
  previousFrameLines,
  selectedToolKey,
  selectedColor,
  strokeWidth,
  onSelectTool,
  onSelectColor,
  onStrokeWidthChange,
  onUndoDrawing,
  onRedoDrawing,
  onClearDrawing,
  onDrawStart,
  onDrawMove,
  onDrawEnd,
  onExit,
  onCompleteRound,
}: FlipbookDrawingViewProps) {
  return (
    <section className="relative min-h-[900px] overflow-hidden border border-flipbook-light bg-flipbook-background text-flipbook-ink">
      <div className="relative mx-auto h-[900px] w-full max-w-[1440px] overflow-hidden">
        <DrawingSessionControls
          tone="flipbook"
          remainingSeconds={remainingSeconds}
          onExit={onExit}
        />

        <aside className="absolute left-[27px] top-[247px] flex h-[481px] w-[225px] flex-col justify-center gap-4 rounded-[18px] bg-flipbook-paper p-5 shadow-[0_4px_16px_10px_var(--color-flipbook-shadow)]">
          <DrawingToolPanel
            tone="flipbook"
            selectedToolKey={selectedToolKey}
            selectedColor={selectedColor}
            strokeWidth={strokeWidth}
            colors={FLIPBOOK_COLORS}
            onSelectTool={onSelectTool}
            onSelectColor={onSelectColor}
            onStrokeWidthChange={onStrokeWidthChange}
            onUndoDrawing={onUndoDrawing}
            onRedoDrawing={onRedoDrawing}
            onClearDrawing={onClearDrawing}
          />
        </aside>

        <main className="absolute left-[269px] top-[128px] h-[720px] w-[850px] overflow-hidden rounded-[16px] border-2 border-flipbook-grid bg-flipbook-paper">
          <FlipbookStage
            lines={lines}
            previousFrameLines={previousFrameLines}
            onDrawStart={onDrawStart}
            onDrawMove={onDrawMove}
            onDrawEnd={onDrawEnd}
          />
          {previousFrameLines.length > 0 && (
            <div className="caption-b pointer-events-none absolute left-5 top-5 rounded-full bg-flipbook-paper/90 px-4 py-2 text-flipbook-deep shadow-[0_4px_12px_var(--color-flipbook-shadow)]">
              {currentParticipant.name} 차례 · 이전 그림을 희미하게 보고 이어 그려요
            </div>
          )}
        </main>

        <aside className="absolute left-[1136px] top-[143px] h-[550px] w-[270px] overflow-hidden rounded-[18px] bg-flipbook-paper p-6 shadow-[0_4px_16px_10px_var(--color-flipbook-shadow)]">
          <div className="flex items-center justify-between">
            <p className="h4-b text-flipbook-ink">라운드 진행</p>
            <p className="h2-b text-flipbook-ink">
              {activeRoundIndex + 1}/{roundCount}
            </p>
          </div>
          <div className="mt-4 h-px bg-flipbook-primary" />
          <p className="h4-b mt-4 text-flipbook-ink">함께하는 친구들</p>
          <div className="mt-4 grid gap-4">
            {FLIPBOOK_PARTICIPANTS.map((participant) => (
              <div
                key={participant.id}
                className="flex min-h-12 items-center gap-3 rounded-[12px] bg-flipbook-light px-3 text-flipbook-deep"
              >
                <span className="grid size-7 place-items-center rounded-full border border-flipbook-paper bg-flipbook-light">
                  {participant.avatar}
                </span>
                <span className="body-b">{participant.name}</span>
              </div>
            ))}
          </div>
          <div className="caption-r mt-4 rounded-[14px] bg-flipbook-primary px-4 py-3 text-flipbook-ink">
            <p className="caption-b">팁</p>
            <p className="mt-1">이전 그림 위에 덧붙이거나 옆에 새로 그려 이야기를 이어가요.</p>
          </div>
        </aside>

        <button
          type="button"
          onClick={onCompleteRound}
          className="body-b absolute left-[1136px] top-[762px] min-h-16 w-[270px] rounded-[16px] bg-flipbook-primary text-flipbook-ink shadow-[0_6px_16px_var(--color-flipbook-shadow)]"
        >
          입력 완료
        </button>
      </div>
    </section>
  )
}
