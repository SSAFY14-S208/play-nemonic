import dynamic from 'next/dynamic'
import { Eye, EyeOff } from 'lucide-react'

import {
  DrawingCompleteButton,
  MobileBrushOpacityBar,
  MobileColorBar,
  MobileToolBar,
  PhoneLauncherButton,
} from '@/shared/components'
import { DRAWING_COLORS, DRAWING_STROKE_WIDTH_OPTIONS } from '@/shared/constants'
import { cn } from '@/shared/libs'
import type {
  DrawingLine,
  DrawingPointerEvent,
  DrawingToolKey,
} from '@/shared/types'
import { FLIPBOOK_BOARD_SIZE } from '@/features/flipbook/constants'
import { useResponsiveElementScale } from '../../hooks'
import { FlipbookDrawingIconButton, SubmissionProgressBadge } from '../DrawingViewControls'

const FlipbookStage = dynamic(() => import('@/features/flipbook/FlipbookStage'), {
  ssr: false,
})

interface MobileDrawingLayoutProps {
  activeRoundIndex: number
  displayRoundCount: number
  remainingSeconds: number
  instructionText: string
  hintToggleLabel: string
  hasOnionSkinHint: boolean
  isOnionSkinVisible: boolean
  isBgmMuted: boolean
  isConnectionUnstable: boolean
  isDrawingLocked: boolean
  overlayMessage: string | null
  submitButtonText: string
  submittedCount: number
  totalCount: number
  errorMessage: string | null
  lines: DrawingLine[]
  previousFrameLines: DrawingLine[]
  selectedToolKey: DrawingToolKey
  selectedColor: string
  selectedOpacity: number
  strokeWidth: number
  canUndoDrawing: boolean
  canRedoDrawing: boolean
  imageSources: {
    howToPlay: string
    soundOn: string
    soundMuted: string
  }
  onOpenHowToPlay: () => void
  onToggleBgmMuted: () => void
  onToggleOnionSkin: () => void
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
  onCompleteRound: () => void
}

export function MobileDrawingLayout({
  activeRoundIndex,
  displayRoundCount,
  remainingSeconds,
  instructionText,
  hintToggleLabel,
  hasOnionSkinHint,
  isOnionSkinVisible,
  isBgmMuted,
  isConnectionUnstable,
  isDrawingLocked,
  overlayMessage,
  submitButtonText,
  submittedCount,
  totalCount,
  errorMessage,
  lines,
  previousFrameLines,
  selectedToolKey,
  selectedColor,
  selectedOpacity,
  strokeWidth,
  canUndoDrawing,
  canRedoDrawing,
  imageSources,
  onOpenHowToPlay,
  onToggleBgmMuted,
  onToggleOnionSkin,
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
}: MobileDrawingLayoutProps) {
  const {
    containerRef: mobileBoardContainerRef,
    elementScale: mobileBoardScale,
  } = useResponsiveElementScale({
    sourceWidth: FLIPBOOK_BOARD_SIZE.width,
    sourceHeight: FLIPBOOK_BOARD_SIZE.height,
  })

  return (
    <div className="relative z-10 grid w-full gap-4 px-3 pb-[calc(1rem+env(safe-area-inset-bottom))] pt-4 lg:hidden">
      <div className="flex items-center justify-end gap-2">
        <FlipbookDrawingIconButton
          imageSrc={imageSources.howToPlay}
          label="게임 설명"
          onClick={onOpenHowToPlay}
        />
        <FlipbookDrawingIconButton
          imageSrc={isBgmMuted ? imageSources.soundMuted : imageSources.soundOn}
          label={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
          pressed={isBgmMuted}
          onClick={onToggleBgmMuted}
        />
        <PhoneLauncherButton className="size-14" />
      </div>

      <div className="rounded-[22px] border border-[#ead7c9] bg-white/90 p-4 shadow-[0_10px_24px_rgb(129_89_54_/_14%)]">
        <div className="flex items-center justify-between gap-3">
          <p className="h2-b text-[#f45d8d]">
            {activeRoundIndex + 1}/{displayRoundCount}
          </p>
          <div className="body-b inline-flex min-h-10 items-center rounded-full border border-[#ead7c9] bg-white px-4 text-[#f45d8d]">
            {remainingSeconds}초
          </div>
        </div>
        <p className="body-b mt-3 text-[#30343b]">{instructionText}</p>
        <button
          type="button"
          onClick={onToggleOnionSkin}
          disabled={!hasOnionSkinHint}
          aria-pressed={hasOnionSkinHint ? isOnionSkinVisible : undefined}
          className={cn(
            'body-b mt-3 inline-flex min-h-10 items-center gap-2 rounded-full border px-4 transition',
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

      <MobileToolBar
        selectedToolKey={selectedToolKey}
        canUndoDrawing={canUndoDrawing}
        canRedoDrawing={canRedoDrawing}
        isDrawingLocked={isDrawingLocked}
        onSelectTool={onSelectTool}
        onUndoDrawing={onUndoDrawing}
        onRedoDrawing={onRedoDrawing}
        onClearDrawing={onClearDrawing}
      />

      <div className="min-w-0 rounded-[18px] border border-[#ead7c9] bg-white p-3 shadow-[0_10px_24px_rgb(129_89_54_/_14%)]">
        <div
          ref={mobileBoardContainerRef}
          className="relative mx-auto w-full max-w-[680px] overflow-hidden rounded-[8px] bg-white"
          style={{
            aspectRatio: `${FLIPBOOK_BOARD_SIZE.width} / ${FLIPBOOK_BOARD_SIZE.height}`,
          }}
        >
          <div
            className="absolute left-1/2 top-1/2 origin-center"
            style={{
              width: FLIPBOOK_BOARD_SIZE.width,
              height: FLIPBOOK_BOARD_SIZE.height,
              transform: `translate(-50%, -50%) scale(${mobileBoardScale})`,
            }}
          >
            <FlipbookStage
              lines={lines}
              previousFrameLines={isOnionSkinVisible ? previousFrameLines : []}
              disabled={isDrawingLocked}
              onDrawStart={onDrawStart}
              onDrawMove={onDrawMove}
              onDrawEnd={onDrawEnd}
            />
          </div>
          {(overlayMessage || isConnectionUnstable) && (
            <div className="body-b absolute inset-0 grid place-items-center bg-[#fff4a7]/72 text-flipbook-deep">
              {isConnectionUnstable ? '연결 상태가 불안정해요. 재연결 중...' : overlayMessage}
            </div>
          )}
        </div>
      </div>

      <MobileColorBar
        colors={DRAWING_COLORS}
        selectedColor={selectedColor}
        isDrawingLocked={isDrawingLocked}
        onSelectColor={onSelectColor}
      />

      <MobileBrushOpacityBar
        strokeWidth={strokeWidth}
        strokeWidthOptions={DRAWING_STROKE_WIDTH_OPTIONS}
        selectedOpacity={selectedOpacity}
        isDrawingLocked={isDrawingLocked}
        onStrokeWidthChange={onStrokeWidthChange}
        onOpacityChange={onOpacityChange}
      />

      <SubmissionProgressBadge
        submittedCount={submittedCount}
        totalCount={totalCount}
      />

      <DrawingCompleteButton
        onComplete={onCompleteRound}
        disabled={isDrawingLocked}
        className="min-h-14 rounded-[16px]"
        label={submitButtonText === '완료!' ? '완료하기' : submitButtonText}
      />
      {errorMessage && (
        <p className="caption-b rounded-[14px] bg-white/90 px-4 py-3 text-center text-flipbook-deep">
          {errorMessage}
        </p>
      )}
    </div>
  )
}
