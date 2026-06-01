'use client'

import Image from 'next/image'

import { HowToPlayModal } from '@/shared/components'
import type {
  DrawingLine,
  DrawingPointerEvent,
  DrawingToolKey,
  FlipbookConnectionStatus,
} from '@/shared/types'
import { FLIPBOOK_HOW_TO_PLAY_PANELS, FLIPBOOK_SOUND_PATHS } from '@/features/flipbook/constants'
import type { FlipbookParticipant } from '@/features/flipbook/types'
import { useFlipbookDrawingViewModel } from './hooks'
import {
  DesktopDrawingLayout,
  DrawingTopControls,
  MobileDrawingLayout,
} from './sections'

const FLIPBOOK_DRAWING_IMAGES = {
  background: '/images/flipbook-lobby/background.png',
  howToPlay: '/images/flipbook-entrance-scene/how-to-play-button.png',
  soundOn: '/images/flipbook-entrance-scene/sound-on-button.png',
  soundMuted: '/images/flipbook-entrance-scene/sound-muted-button.png',
}

interface FlipbookDrawingViewProps {
  activeRoundIndex: number
  roundCount: number | null
  remainingSeconds: number
  currentParticipant: FlipbookParticipant
  isSubmitting: boolean
  isRoundSubmitted: boolean
  isAssignmentReady: boolean
  submittedCount: number
  totalCount: number
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
  submittedCount,
  totalCount,
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
  const {
    audioRef,
    displayRoundCount,
    handleCompleteRound,
    hasOnionSkinHint,
    hintToggleLabel,
    instructionText,
    isBgmMuted,
    isConnectionUnstable,
    isDrawingLocked,
    isHowToPlayModalOpen,
    isOnionSkinVisible,
    openHowToPlayModal,
    overlayMessage,
    setIsHowToPlayModalOpen,
    submitButtonText,
    toggleFlipbookBgmMuted,
    toggleOnionSkinVisibility,
  } = useFlipbookDrawingViewModel({
    activeRoundIndex,
    roundCount,
    isSubmitting,
    isRoundSubmitted,
    isAssignmentReady,
    connectionStatus,
    errorMessage,
    previousFrameCount: previousFrameLines.length,
    onUndoDrawing,
    onRedoDrawing,
    onCompleteRound,
  })

  return (
    <section
      className="relative min-h-screen overflow-y-auto bg-[#fdf1e6] text-[#30343b] lg:grid lg:h-screen lg:overflow-hidden"
      aria-label={currentParticipant.name + ' 플립북 드로잉'}
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

      <audio ref={audioRef} src={FLIPBOOK_SOUND_PATHS.entranceBgm} preload="auto" loop aria-hidden />

      <MobileDrawingLayout
        activeRoundIndex={activeRoundIndex}
        displayRoundCount={displayRoundCount}
        remainingSeconds={remainingSeconds}
        instructionText={instructionText}
        hintToggleLabel={hintToggleLabel}
        hasOnionSkinHint={hasOnionSkinHint}
        isOnionSkinVisible={isOnionSkinVisible}
        isBgmMuted={isBgmMuted}
        isConnectionUnstable={isConnectionUnstable}
        isDrawingLocked={isDrawingLocked}
        overlayMessage={overlayMessage}
        submitButtonText={submitButtonText}
        submittedCount={submittedCount}
        totalCount={totalCount}
        errorMessage={errorMessage}
        lines={lines}
        previousFrameLines={previousFrameLines}
        selectedToolKey={selectedToolKey}
        selectedColor={selectedColor}
        selectedOpacity={selectedOpacity}
        strokeWidth={strokeWidth}
        canUndoDrawing={canUndoDrawing}
        canRedoDrawing={canRedoDrawing}
        imageSources={FLIPBOOK_DRAWING_IMAGES}
        onOpenHowToPlay={openHowToPlayModal}
        onToggleBgmMuted={toggleFlipbookBgmMuted}
        onToggleOnionSkin={toggleOnionSkinVisibility}
        onSelectTool={onSelectTool}
        onSelectColor={onSelectColor}
        onOpacityChange={onOpacityChange}
        onStrokeWidthChange={onStrokeWidthChange}
        onUndoDrawing={onUndoDrawing}
        onRedoDrawing={onRedoDrawing}
        onClearDrawing={onClearDrawing}
        onDrawStart={onDrawStart}
        onDrawMove={onDrawMove}
        onDrawEnd={onDrawEnd}
        onCompleteRound={handleCompleteRound}
      />

      <DesktopDrawingLayout
        activeRoundIndex={activeRoundIndex}
        displayRoundCount={displayRoundCount}
        remainingSeconds={remainingSeconds}
        instructionText={instructionText}
        hasOnionSkinHint={hasOnionSkinHint}
        isOnionSkinVisible={isOnionSkinVisible}
        isConnectionUnstable={isConnectionUnstable}
        isDrawingLocked={isDrawingLocked}
        overlayMessage={overlayMessage}
        submitButtonText={submitButtonText}
        submittedCount={submittedCount}
        totalCount={totalCount}
        errorMessage={errorMessage}
        lines={lines}
        previousFrameLines={previousFrameLines}
        selectedToolKey={selectedToolKey}
        selectedColor={selectedColor}
        selectedOpacity={selectedOpacity}
        strokeWidth={strokeWidth}
        recentColors={recentColors}
        canUndoDrawing={canUndoDrawing}
        canRedoDrawing={canRedoDrawing}
        onToggleOnionSkin={toggleOnionSkinVisibility}
        onSelectTool={onSelectTool}
        onSelectColor={onSelectColor}
        onOpacityChange={onOpacityChange}
        onStrokeWidthChange={onStrokeWidthChange}
        onUndoDrawing={onUndoDrawing}
        onRedoDrawing={onRedoDrawing}
        onClearDrawing={onClearDrawing}
        onDrawStart={onDrawStart}
        onDrawMove={onDrawMove}
        onDrawEnd={onDrawEnd}
        onCompleteRound={handleCompleteRound}
      />

      <DrawingTopControls
        isBgmMuted={isBgmMuted}
        imageSources={FLIPBOOK_DRAWING_IMAGES}
        onOpenHowToPlay={openHowToPlayModal}
        onToggleBgmMuted={toggleFlipbookBgmMuted}
      />

      <HowToPlayModal
        open={isHowToPlayModalOpen}
        onOpenChange={setIsHowToPlayModalOpen}
        panels={FLIPBOOK_HOW_TO_PLAY_PANELS}
        title="플립북 게임 설명"
        subtitle="이전 프레임을 힌트로 보며 조금씩 바꿔 그려 움직이는 플립북을 만들어요."
        accentColor="#ff7182"
      />
    </section>
  )
}
