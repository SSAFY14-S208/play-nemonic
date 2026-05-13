'use client'

import dynamic from 'next/dynamic'
import Image from 'next/image'
import { useEffect, useState } from 'react'
import { Eye, EyeOff, Timer } from 'lucide-react'
import {
  ColorPanel,
  DrawingCompleteButton,
  HintToggleButton,
  MobileColorGrid,
  MobileToolGrid,
  ProgressRail,
  ToolPanel,
  TopStatusBar,
} from '@/shared/components'
import { DRAWING_COLORS } from '@/shared/constants'
import { useDrawingKeyboardShortcuts } from '@/shared/hooks'
import { cn } from '@/shared/libs'
import type {
  DrawingLine,
  DrawingPointerEvent,
  DrawingToolKey,
  FlipbookConnectionStatus,
} from '@/shared/types'
import type { FlipbookDrawingSubmissionState, FlipbookParticipant } from '../types'

const FlipbookStage = dynamic(() => import('../FlipbookStage'), {
  ssr: false,
})

const FLIPBOOK_DRAWING_IMAGES = {
  background: '/images/flipbook-lobby/background.png',
}

interface FlipbookDrawingViewProps {
  activeRoundIndex: number
  roundCount: number | null
  remainingSeconds: number
  currentParticipant: FlipbookParticipant
  isSubmitting: boolean
  isRoundSubmitted: boolean
  isAssignmentReady: boolean
  connectionStatus: FlipbookConnectionStatus
  errorMessage: string | null
  lines: DrawingLine[]
  previousFrameLines: DrawingLine[]
  selectedToolKey: DrawingToolKey
  selectedColor: string
  selectedOpacity: number
  strokeWidth: number
  recentColors: string[]
  canUndoDrawing: boolean
  canRedoDrawing: boolean
  onSelectTool: (toolKey: DrawingToolKey) => void
  onSelectColor: (color: string) => void
  onOpacityChange: (opacity: number) => void
  onStrokeWidthChange: (strokeWidth: number) => void
  onUndoDrawing: () => void
  onRedoDrawing: () => void
  onClearDrawing: () => void
  onDrawStart: (event: DrawingPointerEvent) => void
  onDrawMove: (event: DrawingPointerEvent) => void
  onDrawEnd: () => void
  onCompleteRound: () => void | Promise<void>
}

export default function FlipbookDrawingView({
  activeRoundIndex,
  roundCount,
  remainingSeconds,
  currentParticipant,
  isSubmitting,
  isRoundSubmitted,
  isAssignmentReady,
  connectionStatus,
  errorMessage,
  lines,
  previousFrameLines,
  selectedToolKey,
  selectedColor,
  selectedOpacity,
  strokeWidth,
  recentColors,
  canUndoDrawing,
  canRedoDrawing,
  onSelectTool,
  onSelectColor,
  onOpacityChange,
  onStrokeWidthChange,
  onUndoDrawing,
  onRedoDrawing,
  onClearDrawing,
  onDrawStart,
  onDrawMove,
  onDrawEnd,
  onCompleteRound,
}: FlipbookDrawingViewProps) {
  const [submittedRoundIndex, setSubmittedRoundIndex] = useState<number | null>(null)
  const [isOnionSkinVisible, setIsOnionSkinVisible] = useState(true)
  const isConnectionUnstable =
    connectionStatus === 'reconnecting' || connectionStatus === 'disconnected'
  const displayRoundCount = Math.max(roundCount ?? activeRoundIndex + 1, activeRoundIndex + 1, 1)
  const isWaitingForNextRound =
    (isRoundSubmitted || submittedRoundIndex === activeRoundIndex) && !isSubmitting
  const drawingSubmissionState: FlipbookDrawingSubmissionState = isSubmitting
    ? 'submitting'
    : isWaitingForNextRound
      ? 'waiting'
      : 'drawing'
  const isDrawingLocked =
    !isAssignmentReady || isConnectionUnstable || drawingSubmissionState !== 'drawing'
  const hasOnionSkinHint = previousFrameLines.length > 0
  const instructionText =
    activeRoundIndex === 0
      ? '첫 장면을 그려주세요'
      : hasOnionSkinHint && isOnionSkinVisible
        ? '연하게 보이는 이전 그림을 이어 그려주세요'
        : '다음 장면을 이어 그려주세요'
  const submitButtonText =
    drawingSubmissionState === 'submitting'
      ? '제출 중'
      : drawingSubmissionState === 'waiting'
        ? '대기 중'
        : '완료!'
  const overlayMessage =
    !isAssignmentReady
      ? '그릴 종이를 준비하고 있어요'
      : drawingSubmissionState === 'submitting'
      ? '그림을 제출하고 있어요'
      : drawingSubmissionState === 'waiting'
        ? '제출 완료! 다음 라운드를 기다리는 중이에요'
        : null
  const hintToggleLabel = hasOnionSkinHint
    ? isOnionSkinVisible
      ? '힌트 끄기'
      : '힌트 보기'
    : '힌트 없음'

  const toggleOnionSkinVisibility = () => {
    if (!hasOnionSkinHint) return

    setIsOnionSkinVisible((currentVisibility) => !currentVisibility)
  }

  useDrawingKeyboardShortcuts({
    enabled: !isDrawingLocked,
    onUndo: onUndoDrawing,
    onRedo: onRedoDrawing,
  })

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

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      if (!cancelled) {
        setIsOnionSkinVisible(true)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeRoundIndex, previousFrameLines.length])

  const handleCompleteRound = () => {
    if (isDrawingLocked) return

    setSubmittedRoundIndex(activeRoundIndex)
    void onCompleteRound()
  }

  return (
    <section
      className="relative min-h-screen overflow-y-auto bg-[#fdf1e6] text-[#30343b] lg:grid lg:h-screen lg:place-items-center lg:overflow-hidden"
      aria-label={`${currentParticipant.name} 플립북 드로잉`}
    >
      <Image
        src={FLIPBOOK_DRAWING_IMAGES.background}
        alt=""
        fill
        priority
        sizes="100vw"
        className="pointer-events-none object-cover"
        aria-hidden
      />

      <div className="relative z-10 grid w-full gap-4 px-3 py-4 lg:hidden">
        <div className="rounded-[22px] border border-[#ead7c9] bg-white/90 p-4 shadow-[0_10px_24px_rgb(129_89_54_/_14%)]">
          <div className="flex items-center justify-between gap-3">
            <p className="h2-b text-[#f45d8d]">
              {activeRoundIndex + 1}/{displayRoundCount}
            </p>
            <div className="body-b inline-flex min-h-10 items-center gap-2 rounded-full border border-[#ead7c9] bg-white px-4 text-[#f45d8d]">
              <Timer className="size-5" aria-hidden />
              {remainingSeconds}초
            </div>
          </div>
          <p className="body-b mt-3 text-[#30343b]">{instructionText}</p>
          <button
            type="button"
            onClick={toggleOnionSkinVisibility}
            disabled={!hasOnionSkinHint}
            aria-pressed={hasOnionSkinHint ? isOnionSkinVisible : undefined}
            className={cn(
              'body-b mt-4 inline-flex min-h-10 items-center gap-2 rounded-full border px-4 transition',
              hasOnionSkinHint
                ? isOnionSkinVisible
                  ? 'border-[#ff8bab] bg-[#ffecf3] text-[#db4d82]'
                  : 'border-[#ead7c9] bg-white text-[#7d6251]'
                : 'cursor-not-allowed border-[#ead7c9] bg-[#f7efe7] text-[#b9a799]',
            )}
          >
            {isOnionSkinVisible && hasOnionSkinHint ? (
              <Eye className="size-5" aria-hidden />
            ) : (
              <EyeOff className="size-5" aria-hidden />
            )}
            {hintToggleLabel}
          </button>
        </div>

        <MobileToolGrid
          selectedToolKey={selectedToolKey}
          canUndoDrawing={canUndoDrawing}
          canRedoDrawing={canRedoDrawing}
          isDrawingLocked={isDrawingLocked}
          onSelectTool={onSelectTool}
          onUndoDrawing={onUndoDrawing}
          onRedoDrawing={onRedoDrawing}
          onClearDrawing={onClearDrawing}
        />

        <MobileColorGrid
          colors={DRAWING_COLORS}
          selectedColor={selectedColor}
          selectedOpacity={selectedOpacity}
          strokeWidth={strokeWidth}
          isDrawingLocked={isDrawingLocked}
          onSelectColor={onSelectColor}
          onOpacityChange={onOpacityChange}
          onStrokeWidthChange={onStrokeWidthChange}
        />

        <div className="overflow-x-auto rounded-[18px] border border-[#ead7c9] bg-white p-3 shadow-[0_10px_24px_rgb(129_89_54_/_14%)]">
          <div className="relative h-[520px] w-[680px] overflow-hidden rounded-[8px] bg-white">
            <FlipbookStage
              lines={lines}
              previousFrameLines={isOnionSkinVisible ? previousFrameLines : []}
              disabled={isDrawingLocked}
              onDrawStart={onDrawStart}
              onDrawMove={onDrawMove}
              onDrawEnd={onDrawEnd}
            />
            {(overlayMessage || isConnectionUnstable) && (
              <div className="body-b absolute inset-0 grid place-items-center bg-[#fff4a7]/72 text-flipbook-deep">
                {isConnectionUnstable ? '연결 끊김 — 재연결 중...' : overlayMessage}
              </div>
            )}
          </div>
        </div>

        <DrawingCompleteButton
          onComplete={handleCompleteRound}
          disabled={isDrawingLocked}
          className={cn(
            'min-h-14 rounded-[16px]',
          )}
          label={submitButtonText === '완료!' ? '완료하기' : submitButtonText}
        />
        {errorMessage && (
          <p className="caption-b rounded-[14px] bg-white/90 px-4 py-3 text-center text-flipbook-deep">
            {errorMessage}
          </p>
        )}
      </div>

      <div className="relative hidden h-[819.2px] w-[1228.8px] shrink-0 lg:block">
        <div className="absolute left-0 top-0 h-[1024px] w-[1536px] origin-top-left scale-[0.8]">
          <TopStatusBar
            activeRoundIndex={activeRoundIndex}
            roundCount={displayRoundCount}
            remainingSeconds={remainingSeconds}
            instructionText={instructionText}
          />

          <ColorPanel
            className={cn(isDrawingLocked && 'pointer-events-none opacity-60')}
            colors={DRAWING_COLORS}
            selectedColor={selectedColor}
            selectedOpacity={selectedOpacity}
            strokeWidth={strokeWidth}
            recentColors={recentColors}
            onSelectColor={onSelectColor}
            onOpacityChange={onOpacityChange}
            onStrokeWidthChange={onStrokeWidthChange}
          />

          <main className="absolute left-[345px] top-[218px] h-[689px] w-[900px]">
            <div className="absolute inset-0 rounded-[8px] bg-white shadow-[0_8px_42px_-10px_rgb(0_0_0_/_25%)]" />
            <div className="absolute inset-0 z-10 overflow-hidden rounded-[4px] bg-white">
              <div className="h-[520px] w-[680px] origin-top-left scale-[1.323529]">
                <FlipbookStage
                  lines={lines}
                  previousFrameLines={isOnionSkinVisible ? previousFrameLines : []}
                  disabled={isDrawingLocked}
                  onDrawStart={onDrawStart}
                  onDrawMove={onDrawMove}
                  onDrawEnd={onDrawEnd}
                />
              </div>
              {overlayMessage && (
                <div className="absolute inset-0 grid place-items-center bg-[#fff4a7]/70 text-flipbook-deep">
                  <div className="rounded-[14px] bg-flipbook-paper/92 px-6 py-4 text-center shadow-[0_4px_12px_var(--color-flipbook-shadow)]">
                    <p className="body-l-b">{overlayMessage}</p>
                    <p className="caption-m mt-2 text-flipbook-deep/75">
                      캔버스는 잠시 잠겨 있어요
                    </p>
                  </div>
                </div>
              )}
              {isConnectionUnstable && (
                <div className="body-b absolute inset-0 grid place-items-center bg-[#fff4a7]/72 text-flipbook-deep">
                  연결 끊김 — 재연결 중...
                </div>
              )}
            </div>
          </main>

          <ToolPanel
            className={cn(isDrawingLocked && 'pointer-events-none opacity-60')}
            selectedToolKey={selectedToolKey}
            canUndoDrawing={canUndoDrawing}
            canRedoDrawing={canRedoDrawing}
            onSelectTool={onSelectTool}
            onUndoDrawing={onUndoDrawing}
            onRedoDrawing={onRedoDrawing}
            onClearDrawing={onClearDrawing}
          />

          <HintToggleButton
            className="absolute left-[70px] top-[928px]"
            hasOnionSkinHint={hasOnionSkinHint}
            isOnionSkinVisible={isOnionSkinVisible}
            onToggle={toggleOnionSkinVisibility}
          />

          <ProgressRail activeRoundIndex={activeRoundIndex} roundCount={displayRoundCount} />

          <DrawingCompleteButton
            onComplete={handleCompleteRound}
            disabled={isDrawingLocked}
            className={cn(
              'absolute left-[1254px] top-[928px] h-[62px] w-[222px]',
            )}
            label={submitButtonText === '완료!' ? '완료하기' : submitButtonText}
          />
          {errorMessage && (
            <p className="caption-b absolute left-[345px] top-[908px] w-[900px] text-center text-flipbook-deep">
              {errorMessage}
            </p>
          )}
        </div>
      </div>
    </section>
  )
}
