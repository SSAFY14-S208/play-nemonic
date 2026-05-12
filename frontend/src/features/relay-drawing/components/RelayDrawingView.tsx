'use client'

import dynamic from 'next/dynamic'
import Image from 'next/image'
import {
  ColorPanel,
  DrawingCompleteButton,
  MobileColorGrid,
  MobileToolGrid,
  ProgressRail,
  ToolPanel,
  TopStatusBar,
} from '@/shared/components'
import { DRAWING_COLORS, DRAWING_STROKE_WIDTH_OPTIONS } from '@/shared/constants'
import { useDrawingKeyboardShortcuts } from '@/shared/hooks'
import { cn } from '@/shared/libs'
import type { DrawingToolKey } from '@/shared/types'
import {
  RELAY_ROUND_ORDER,
  RELAY_ROUND_SEGMENTS,
} from '../constants'
import { useRelayDrawingGame } from '../hooks/useRelayDrawingGame'
import { useRelayTimer } from '../hooks/useRelayTimer'
import { useRelayDrawingStore } from '../stores'
import PartTimeUpOverlay from './PartTimeUpOverlay'

const RelayDrawingStage = dynamic(() => import('../RelayDrawingStage'), {
  ssr: false,
})

const RELAY_DRAWING_IMAGES = {
  background: '/images/flipbook-lobby/background.png',
}

export default function RelayDrawingView() {
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey)
  const isPartTimeUp = useRelayDrawingStore((state) => state.isPartTimeUp)
  const selectedToolKey = useRelayDrawingStore((state) => state.selectedToolKey)
  const selectedColor = useRelayDrawingStore((state) => state.selectedColor)
  const selectedOpacity = useRelayDrawingStore((state) => state.selectedOpacity)
  const strokeWidth = useRelayDrawingStore((state) => state.strokeWidth)
  const recentColors = useRelayDrawingStore((state) => state.recentColors)
  const roundLines = useRelayDrawingStore((state) => state.roundLines)
  const roundRedoStack = useRelayDrawingStore((state) => state.roundRedoStack)
  const setSelectedToolKey = useRelayDrawingStore((state) => state.setSelectedToolKey)
  const setSelectedColor = useRelayDrawingStore((state) => state.setSelectedColor)
  const setSelectedOpacity = useRelayDrawingStore((state) => state.setSelectedOpacity)
  const setStrokeWidth = useRelayDrawingStore((state) => state.setStrokeWidth)
  const undoLine = useRelayDrawingStore((state) => state.undoLine)
  const redoLine = useRelayDrawingStore((state) => state.redoLine)
  const clearRoundLines = useRelayDrawingStore((state) => state.clearRoundLines)
  const { remainingSeconds, formattedTime } = useRelayTimer()
  const {
    submitDrawing,
    isSubmitting,
    isSubmitted,
    submittedCount,
    totalCount,
  } = useRelayDrawingGame()

  const activeRound = RELAY_ROUND_SEGMENTS[activeRoundKey]
  const activeRoundIndex = RELAY_ROUND_ORDER.findIndex((roundKey) => roundKey === activeRoundKey)
  const isLastRound = activeRoundKey === 'legs'
  const canUndoDrawing = roundLines[activeRoundKey].length > 0
  const canRedoDrawing = roundRedoStack[activeRoundKey].length > 0
  const isDrawingLocked = isSubmitting || isSubmitted || isPartTimeUp
  const completionStatusText =
    isSubmitted && totalCount > 0 ? ` (${submittedCount}/${totalCount})` : ''
  const buttonLabel = (() => {
    if (isSubmitting) return '제출 중'
    if (isSubmitted) return `대기 중${completionStatusText}`
    return isLastRound ? '완료하기' : `${activeRound.label} 저장하기`
  })()
  const overlayMessage =
    isPartTimeUp
      ? '다음 파트를 준비하고 있어요'
      : isSubmitted
        ? '제출 완료! 다음 파트를 기다리는 중이에요'
        : isSubmitting
          ? '그림을 제출하고 있어요'
          : null

  const handleSelectTool = (toolKey: DrawingToolKey) => {
    if (toolKey === 'marker') return
    setSelectedToolKey(toolKey)
  }

  const handleSubmitClick = () => {
    void submitDrawing()
  }

  useDrawingKeyboardShortcuts({
    enabled: !isDrawingLocked,
    onUndo: undoLine,
    onRedo: redoLine,
  })

  return (
    <section
      className="relative min-h-screen overflow-y-auto bg-[#fdf1e6] text-[#30343b] lg:grid lg:h-screen lg:place-items-center lg:overflow-hidden"
      aria-label="릴레이 드로잉"
    >
      <Image
        src={RELAY_DRAWING_IMAGES.background}
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
              {activeRoundIndex + 1}/{RELAY_ROUND_ORDER.length}
            </p>
            <div className="body-b inline-flex min-h-10 items-center rounded-full border border-[#ead7c9] bg-white px-4 text-[#f45d8d]">
              {formattedTime}
            </div>
          </div>
          <p className="body-b mt-3 text-[#30343b]">{activeRound.helperText}</p>
        </div>

        <MobileToolGrid
          selectedToolKey={selectedToolKey}
          canUndoDrawing={canUndoDrawing}
          canRedoDrawing={canRedoDrawing}
          isDrawingLocked={isDrawingLocked}
          onSelectTool={handleSelectTool}
          onUndoDrawing={undoLine}
          onRedoDrawing={redoLine}
          onClearDrawing={clearRoundLines}
        />

        <MobileColorGrid
          colors={DRAWING_COLORS}
          selectedColor={selectedColor}
          selectedOpacity={selectedOpacity}
          strokeWidth={strokeWidth}
          strokeWidthOptions={DRAWING_STROKE_WIDTH_OPTIONS}
          isDrawingLocked={isDrawingLocked}
          onSelectColor={setSelectedColor}
          onOpacityChange={setSelectedOpacity}
          onStrokeWidthChange={setStrokeWidth}
        />

        <div className="overflow-x-auto rounded-[18px] border border-[#ead7c9] bg-white p-3 shadow-[0_10px_24px_rgb(129_89_54_/_14%)]">
          <div className="relative h-[720px] w-[848px] overflow-hidden rounded-[8px] bg-white">
            <RelayDrawingStage />
            {overlayMessage && (
              <div className="body-b absolute inset-0 grid place-items-center bg-[#fff4a7]/72 text-[#30343b]">
                {overlayMessage}
              </div>
            )}
          </div>
        </div>

        <DrawingCompleteButton
          onComplete={handleSubmitClick}
          disabled={isDrawingLocked}
          className="min-h-14 rounded-[16px]"
          label={buttonLabel}
        />
      </div>

      <div className="relative hidden h-[819.2px] w-[1228.8px] shrink-0 lg:block">
        <div className="absolute left-0 top-0 h-[1024px] w-[1536px] origin-top-left scale-[0.8]">
          <TopStatusBar
            activeRoundIndex={activeRoundIndex}
            roundCount={RELAY_ROUND_ORDER.length}
            remainingSeconds={remainingSeconds}
            remainingTimeLabel={formattedTime}
            timerUnitLabel=""
            instructionText={activeRound.helperText}
          />

          <ColorPanel
            className={cn(isDrawingLocked && 'pointer-events-none opacity-60')}
            colors={DRAWING_COLORS}
            selectedColor={selectedColor}
            selectedOpacity={selectedOpacity}
            strokeWidth={strokeWidth}
            strokeWidthOptions={DRAWING_STROKE_WIDTH_OPTIONS}
            recentColors={recentColors}
            onSelectColor={setSelectedColor}
            onOpacityChange={setSelectedOpacity}
            onStrokeWidthChange={setStrokeWidth}
          />

          <main className="absolute left-[345px] top-[188px] h-[720px] w-[848px]">
            <div className="absolute inset-0 rounded-[8px] bg-white shadow-[0_8px_42px_-10px_rgb(0_0_0_/_25%)]" />
            <div className="absolute inset-0 z-10 overflow-hidden rounded-[4px] bg-white">
              <RelayDrawingStage />
              {overlayMessage && (
                <div className="body-b absolute inset-0 grid place-items-center bg-[#fff4a7]/72 text-[#30343b]">
                  {overlayMessage}
                </div>
              )}
            </div>
          </main>

          <ToolPanel
            className={cn(isDrawingLocked && 'pointer-events-none opacity-60')}
            selectedToolKey={selectedToolKey}
            canUndoDrawing={canUndoDrawing}
            canRedoDrawing={canRedoDrawing}
            onSelectTool={handleSelectTool}
            onUndoDrawing={undoLine}
            onRedoDrawing={redoLine}
            onClearDrawing={clearRoundLines}
          />

          <ProgressRail
            activeRoundIndex={activeRoundIndex}
            roundCount={RELAY_ROUND_ORDER.length}
          />

          <DrawingCompleteButton
            onComplete={handleSubmitClick}
            disabled={isDrawingLocked}
            className="absolute left-[1254px] top-[928px] h-[62px] w-[222px]"
            label={buttonLabel}
          />
        </div>
      </div>

      <PartTimeUpOverlay isVisible={isPartTimeUp} isLastRound={isLastRound} />
    </section>
  )
}
