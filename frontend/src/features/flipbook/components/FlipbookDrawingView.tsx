'use client'

import dynamic from 'next/dynamic'
import { Check } from 'lucide-react'
import { DrawingSessionControls, DrawingToolPanel } from '@/shared/components'
import { cn } from '@/shared/libs'
import type {
  DrawingLine,
  DrawingPointerEvent,
  DrawingToolKey,
  FlipbookConnectionStatus,
} from '@/shared/types'
import { FLIPBOOK_COLORS, type FlipbookParticipant } from '../constants'

const FlipbookStage = dynamic(() => import('../FlipbookStage'), {
  ssr: false,
})

const FLIPBOOK_STROKE_WIDTH_OPTIONS = [3, 6, 9, 12, 16]
const CORNER_DOT_POSITIONS = Array.from({ length: 9 }, (unusedValue, dotIndex) => ({
  id: `flipbook-dot-${dotIndex}`,
  left: (dotIndex % 3) * 16,
  top: Math.floor(dotIndex / 3) * 16,
}))

interface FlipbookDrawingViewProps {
  activeRoundIndex: number
  roundCount: number
  remainingSeconds: number
  currentParticipant: FlipbookParticipant
  isSubmitting: boolean
  connectionStatus: FlipbookConnectionStatus
  errorMessage: string | null
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
  isSubmitting,
  connectionStatus,
  errorMessage,
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
  const instructionText =
    activeRoundIndex > 0 ? '앞사람의 그림을 이어 그려주세요' : '첫 장면을 그려주세요'
  const isConnectionUnstable =
    connectionStatus === 'reconnecting' || connectionStatus === 'disconnected'

  return (
    <section
      className="relative min-h-screen overflow-hidden bg-flipbook-background text-flipbook-ink"
      aria-label={`${currentParticipant.name} 플립북 드로잉`}
    >
      <div className="relative mx-auto h-[830px] w-full max-w-[1440px] overflow-hidden">
        <CornerDots className="left-10 top-10" />
        <CornerDots className="right-10 top-10" />

        <DrawingSessionControls
          tone="flipbook"
          remainingSeconds={remainingSeconds}
          exitButtonClassName="left-10 top-[112px] min-h-10 bg-flipbook-paper/75 px-4 text-flipbook-deep shadow-[0_4px_12px_var(--color-flipbook-shadow)]"
          timerBadgeClassName="left-[1107px] top-14 min-h-[73px] min-w-[138px] gap-2 bg-[#2a1f3a] px-[14px] text-[40px] font-bold leading-none [&_svg]:size-[45px]"
          onExit={onExit}
        />

        <p className="absolute left-[210px] top-[43px] text-[60px] font-bold leading-none text-flipbook-primary">
          {activeRoundIndex + 1}/{roundCount}
        </p>

        <aside className="absolute left-[161px] top-[225px] rounded-[14px] bg-[#edbfc4] p-[19px] shadow-[0_4px_12px_0_rgba(92,31,38,0.3)]">
          <DrawingToolPanel
            tone="flipbook"
            sections={['colors']}
            showSectionLabels={false}
            showSelectedColorPreview
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

        <main className="absolute left-[380px] top-[72px] h-[600px] w-[680px]">
          <div className="absolute inset-x-0 top-0 h-20 rounded-t-[16px] bg-flipbook-primary shadow-[0_4px_12px_0_rgba(92,31,38,0.2)]">
            <div className="absolute left-[140px] top-[-17px] size-[34px] rounded-full bg-flipbook-primary">
              <div className="absolute left-[11px] top-[11px] size-3 rounded-full bg-[#ffb1c0]" />
            </div>
            <div className="absolute right-[140px] top-[-17px] size-[34px] rounded-full bg-flipbook-primary">
              <div className="absolute left-[11px] top-[11px] size-3 rounded-full bg-[#ffb1c0]" />
            </div>
            <p className="body-l-b absolute inset-x-0 top-[27px] text-center text-flipbook-light">
              {instructionText}
            </p>
          </div>
          <div className="absolute inset-x-0 top-20 h-[520px] overflow-hidden rounded-b-[16px] bg-white">
            <FlipbookStage
              lines={lines}
              previousFrameLines={previousFrameLines}
              onDrawStart={onDrawStart}
              onDrawMove={onDrawMove}
              onDrawEnd={onDrawEnd}
            />
            {isConnectionUnstable && (
              <div className="body-b absolute inset-0 grid place-items-center bg-white/72 text-flipbook-deep">
                연결 끊김 — 재연결 중...
              </div>
            )}
          </div>
        </main>

        <aside className="absolute left-[1099px] top-[225px] rounded-[14px] bg-[#edbfc4] p-[18px] shadow-[0_4px_12px_0_rgba(92,31,38,0.3)]">
          <DrawingToolPanel
            tone="flipbook"
            sections={['tools']}
            showSectionLabels={false}
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

        <div className="absolute left-[147px] top-[156px]">
          <DrawingToolPanel
            tone="flipbook"
            sections={['stroke']}
            showSectionLabels={false}
            strokeWidthOptions={FLIPBOOK_STROKE_WIDTH_OPTIONS}
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
        </div>

        <button
          type="button"
          onClick={onCompleteRound}
          disabled={isSubmitting}
          className="absolute left-[1099px] top-[565px] inline-flex h-20 w-[180px] items-center justify-center gap-4 rounded-[16px] border-[3px] border-[#aacfe8] bg-flipbook-primary text-flipbook-ink shadow-[0_4px_12px_0_rgba(92,31,38,0.3)]"
        >
          <span className="grid size-[34px] place-items-center rounded-full bg-white">
            <Check className="size-5 text-flipbook-primary" aria-hidden />
          </span>
          <span className="h2-b">{isSubmitting ? '제출 중' : '완료!'}</span>
        </button>
        {errorMessage && (
          <p className="caption-b absolute left-[380px] top-[690px] w-[680px] text-center text-flipbook-deep">
            {errorMessage}
          </p>
        )}
      </div>
    </section>
  )
}

function CornerDots({ className }: { className: string }) {
  return (
    <div aria-hidden className={cn('pointer-events-none absolute size-9', className)}>
      {CORNER_DOT_POSITIONS.map((dotPosition) => (
        <span
          key={dotPosition.id}
          className="absolute size-1 rounded-full bg-flipbook-primary"
          style={{
            left: dotPosition.left,
            top: dotPosition.top,
          }}
        />
      ))}
    </div>
  )
}
