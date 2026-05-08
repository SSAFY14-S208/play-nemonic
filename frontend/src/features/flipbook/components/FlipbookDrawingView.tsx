'use client'

import dynamic from 'next/dynamic'
import { useEffect, useState } from 'react'
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
import type { FlipbookDrawingSubmissionState } from '../types'

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
  isRoundSubmitted: boolean
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
  onCompleteRound: () => void | Promise<void>
}

export default function FlipbookDrawingView({
  activeRoundIndex,
  roundCount,
  remainingSeconds,
  currentParticipant,
  isSubmitting,
  isRoundSubmitted,
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
  const [submittedRoundIndex, setSubmittedRoundIndex] = useState<number | null>(null)
  const isConnectionUnstable =
    connectionStatus === 'reconnecting' || connectionStatus === 'disconnected'
  const isWaitingForNextRound =
    (isRoundSubmitted || submittedRoundIndex === activeRoundIndex) && !isSubmitting
  const drawingSubmissionState: FlipbookDrawingSubmissionState = isSubmitting
    ? 'submitting'
    : isWaitingForNextRound
      ? 'waiting'
      : 'drawing'
  const isDrawingLocked = isConnectionUnstable || drawingSubmissionState !== 'drawing'
  const hasOnionSkinHint = previousFrameLines.length > 0
  const instructionText =
    activeRoundIndex === 0
      ? '첫 장면을 그려주세요'
      : hasOnionSkinHint
        ? '연하게 보이는 이전 그림을 이어 그려주세요'
        : '다음 장면을 이어 그려주세요'
  const submitButtonText =
    drawingSubmissionState === 'submitting'
      ? '제출 중'
      : drawingSubmissionState === 'waiting'
        ? '대기 중'
        : '완료!'
  const overlayMessage =
    drawingSubmissionState === 'submitting'
      ? '그림을 제출하고 있어요'
      : drawingSubmissionState === 'waiting'
        ? '제출 완료! 다음 라운드를 기다리는 중이에요'
        : null

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (submittedRoundIndex === null) return

      const shouldUnlockDrawing =
        (submittedRoundIndex !== activeRoundIndex && !isRoundSubmitted) || errorMessage !== null
      if (!cancelled && shouldUnlockDrawing) {
        setSubmittedRoundIndex(null)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeRoundIndex, errorMessage, isRoundSubmitted, submittedRoundIndex])

  const handleCompleteRound = () => {
    if (isDrawingLocked) return

    setSubmittedRoundIndex(activeRoundIndex)
    void onCompleteRound()
  }

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
          <div className={cn(isDrawingLocked && 'pointer-events-none opacity-60')}>
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
          </div>
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
            {hasOnionSkinHint && (
              <p className="caption-b absolute right-5 top-[30px] text-flipbook-light/80">
                오니언스킨
              </p>
            )}
          </div>
          <div className="absolute inset-x-0 top-20 h-[520px] overflow-hidden rounded-b-[16px] bg-white">
            <FlipbookStage
              lines={lines}
              previousFrameLines={previousFrameLines}
              disabled={isDrawingLocked}
              onDrawStart={onDrawStart}
              onDrawMove={onDrawMove}
              onDrawEnd={onDrawEnd}
            />
            {overlayMessage && (
              <div className="absolute inset-0 grid place-items-center bg-white/68 text-flipbook-deep">
                <div className="rounded-[14px] bg-flipbook-paper/92 px-6 py-4 text-center shadow-[0_4px_12px_var(--color-flipbook-shadow)]">
                  <p className="body-l-b">{overlayMessage}</p>
                  <p className="caption-m mt-2 text-flipbook-deep/75">
                    캔버스는 잠시 잠겨 있어요
                  </p>
                </div>
              </div>
            )}
            {isConnectionUnstable && (
              <div className="body-b absolute inset-0 grid place-items-center bg-white/72 text-flipbook-deep">
                연결 끊김 — 재연결 중...
              </div>
            )}
          </div>
        </main>

        <aside className="absolute left-[1099px] top-[225px] rounded-[14px] bg-[#edbfc4] p-[18px] shadow-[0_4px_12px_0_rgba(92,31,38,0.3)]">
          <div className={cn(isDrawingLocked && 'pointer-events-none opacity-60')}>
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
          </div>
        </aside>

        <div className="absolute left-[147px] top-[156px]">
          <div className={cn(isDrawingLocked && 'pointer-events-none opacity-60')}>
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
        </div>

        <button
          type="button"
          onClick={handleCompleteRound}
          disabled={isDrawingLocked}
          className={cn(
            'absolute left-[1099px] top-[565px] inline-flex h-20 w-[180px] items-center justify-center gap-4 rounded-[16px] border-[3px] border-[#aacfe8] bg-flipbook-primary text-flipbook-ink shadow-[0_4px_12px_0_rgba(92,31,38,0.3)]',
            isDrawingLocked && 'cursor-not-allowed opacity-70',
          )}
        >
          <span className="grid size-[34px] place-items-center rounded-full bg-white">
            <Check className="size-5 text-flipbook-primary" aria-hidden />
          </span>
          <span className="h2-b">{submitButtonText}</span>
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
