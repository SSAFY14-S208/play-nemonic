import dynamic from 'next/dynamic'

import {
  ColorPanel,
  DrawingCompleteButton,
  HintToggleButton,
  ProgressRail,
  ToolPanel,
  TopStatusBar,
} from '@/shared/components'
import { DRAWING_COLORS } from '@/shared/constants'
import { cn } from '@/shared/libs'
import type {
  DrawingLine,
  DrawingPointerEvent,
  DrawingToolKey,
} from '@/shared/types'
import { useResponsiveElementScale } from '../../hooks'
import { SubmissionProgressBadge } from '../DrawingViewControls'

const FlipbookStage = dynamic(() => import('@/features/flipbook/FlipbookStage'), {
  ssr: false,
})

const DESKTOP_DESIGN_WIDTH = 1536
const DESKTOP_DESIGN_HEIGHT = 1024

interface DesktopDrawingLayoutProps {
  activeRoundIndex: number
  displayRoundCount: number
  remainingSeconds: number
  instructionText: string
  hasOnionSkinHint: boolean
  isOnionSkinVisible: boolean
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
  recentColors: string[]
  canUndoDrawing: boolean
  canRedoDrawing: boolean
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

export function DesktopDrawingLayout({
  activeRoundIndex,
  displayRoundCount,
  remainingSeconds,
  instructionText,
  hasOnionSkinHint,
  isOnionSkinVisible,
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
  recentColors,
  canUndoDrawing,
  canRedoDrawing,
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
}: DesktopDrawingLayoutProps) {
  const {
    containerRef: desktopWrapperRef,
    elementScale: desktopScale,
  } = useResponsiveElementScale({
    sourceWidth: DESKTOP_DESIGN_WIDTH,
    sourceHeight: DESKTOP_DESIGN_HEIGHT,
  })

  return (
    <div
      ref={desktopWrapperRef}
      className="relative hidden lg:block lg:h-full lg:w-full"
    >
      <div
        className="absolute left-1/2 top-1/2 origin-center"
        style={{
          width: DESKTOP_DESIGN_WIDTH,
          height: DESKTOP_DESIGN_HEIGHT,
          transform: `translate(-50%, -50%) scale(${desktopScale})`,
        }}
      >
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
                연결 상태가 불안정해요. 재연결 중...
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
          onToggle={onToggleOnionSkin}
        />

        <ProgressRail activeRoundIndex={activeRoundIndex} roundCount={displayRoundCount} />

        <SubmissionProgressBadge
          className="absolute left-[1254px] top-[884px] h-9 w-[222px]"
          submittedCount={submittedCount}
          totalCount={totalCount}
        />
        <DrawingCompleteButton
          onComplete={onCompleteRound}
          disabled={isDrawingLocked}
          className="absolute left-[1254px] top-[928px] h-[62px] w-[222px]"
          label={submitButtonText === '완료!' ? '완료하기' : submitButtonText}
        />
        {errorMessage && (
          <p className="caption-b absolute left-[345px] top-[908px] w-[900px] text-center text-flipbook-deep">
            {errorMessage}
          </p>
        )}
      </div>
    </div>
  )
}
