'use client'

import dynamic from 'next/dynamic'
import Image from 'next/image'
import { useEffect, useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import {
  ColorPanel,
  DrawingCompleteButton,
  HintToggleButton,
  HowToPlayModal,
  MobileBrushOpacityBar,
  MobileColorBar,
  MobileToolBar,
  PhoneLauncherButton,
  ProgressRail,
  ToolPanel,
  TopStatusBar,
} from '@/shared/components'
import { DRAWING_COLORS, DRAWING_STROKE_WIDTH_OPTIONS } from '@/shared/constants'
import { useDrawingKeyboardShortcuts } from '@/shared/hooks'
import { cn } from '@/shared/libs'
import type {
  DrawingLine,
  DrawingPointerEvent,
  DrawingToolKey,
  FlipbookConnectionStatus,
} from '@/shared/types'
import {
  FLIPBOOK_BOARD_SIZE,
  FLIPBOOK_HOW_TO_PLAY_PANELS,
  FLIPBOOK_SOUND_PATHS,
} from '../constants'
import type { FlipbookDrawingSubmissionState, FlipbookParticipant } from '../types'
import { useFlipbookEntranceBgm, useResponsiveElementScale } from '../hooks'

const FlipbookStage = dynamic(() => import('../FlipbookStage'), {
  ssr: false,
})

const FLIPBOOK_DRAWING_IMAGES = {
  background: '/images/flipbook-lobby/background.png',
  howToPlay: '/images/flipbook-entrance-scene/how-to-play-button.png',
  soundOn: '/images/flipbook-entrance-scene/sound-on-button.png',
  soundMuted: '/images/flipbook-entrance-scene/sound-muted-button.png',
}

// 데스크탑(lg+) 그리기 화면은 1536×1024 디자인을 기준으로 절대 좌표로 배치되어
// 있다. 작은 viewport에선 디자인 그대로 두면 클리핑되므로, 부모 크기를 측정해
// 가로/세로 중 더 작은 비율로 scale을 동적으로 잡는다.
const DESKTOP_DESIGN_WIDTH = 1536
const DESKTOP_DESIGN_HEIGHT = 1024

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
  const [isHowToPlayModalOpen, setIsHowToPlayModalOpen] = useState(false)
  const { audioRef, isBgmMuted, toggleFlipbookEntranceBgmMuted } = useFlipbookEntranceBgm({
    shouldStart: true,
  })
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

  const {
    containerRef: mobileBoardContainerRef,
    elementScale: mobileBoardScale,
  } = useResponsiveElementScale({
    sourceWidth: FLIPBOOK_BOARD_SIZE.width,
    sourceHeight: FLIPBOOK_BOARD_SIZE.height,
  })
  const {
    containerRef: desktopWrapperRef,
    elementScale: desktopScale,
  } = useResponsiveElementScale({
    sourceWidth: DESKTOP_DESIGN_WIDTH,
    sourceHeight: DESKTOP_DESIGN_HEIGHT,
  })

  const handleCompleteRound = () => {
    if (isDrawingLocked) return

    setSubmittedRoundIndex(activeRoundIndex)
    void onCompleteRound()
  }

  return (
    <section
      className="relative min-h-screen overflow-y-auto bg-[#fdf1e6] text-[#30343b] lg:grid lg:h-screen lg:overflow-hidden"
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

      <audio ref={audioRef} src={FLIPBOOK_SOUND_PATHS.entranceBgm} preload="auto" loop aria-hidden />

      <div className="relative z-10 grid w-full gap-4 px-3 pb-[calc(1rem+env(safe-area-inset-bottom))] pt-4 lg:hidden">
        <div className="flex items-center justify-end gap-2">
          <FlipbookDrawingIconButton
            imageSrc={FLIPBOOK_DRAWING_IMAGES.howToPlay}
            label="게임 설명"
            onClick={() => setIsHowToPlayModalOpen(true)}
          />
          <FlipbookDrawingIconButton
            imageSrc={isBgmMuted ? FLIPBOOK_DRAWING_IMAGES.soundMuted : FLIPBOOK_DRAWING_IMAGES.soundOn}
            label={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
            pressed={isBgmMuted}
            onClick={toggleFlipbookEntranceBgmMuted}
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
            onClick={toggleOnionSkinVisibility}
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
                {isConnectionUnstable ? '연결 끊김 — 재연결 중...' : overlayMessage}
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
          onComplete={handleCompleteRound}
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

          <SubmissionProgressBadge
            className="absolute left-[1254px] top-[884px] h-9 w-[222px]"
            submittedCount={submittedCount}
            totalCount={totalCount}
          />
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
      <div className="fixed right-4 top-4 z-[var(--z-sticky)] hidden items-center gap-2 lg:flex">
        <FlipbookDrawingIconButton
          imageSrc={FLIPBOOK_DRAWING_IMAGES.howToPlay}
          label="게임 설명"
          onClick={() => setIsHowToPlayModalOpen(true)}
        />
        <FlipbookDrawingIconButton
          imageSrc={isBgmMuted ? FLIPBOOK_DRAWING_IMAGES.soundMuted : FLIPBOOK_DRAWING_IMAGES.soundOn}
          label={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
          pressed={isBgmMuted}
          onClick={toggleFlipbookEntranceBgmMuted}
        />
        <PhoneLauncherButton />
      </div>

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

function FlipbookDrawingIconButton({
  imageSrc,
  label,
  pressed,
  onClick,
}: {
  imageSrc: string
  label: string
  pressed?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      title={label}
      className="relative grid size-14 place-items-center transition duration-150 hover:-translate-y-0.5 active:translate-y-px active:scale-95 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-flipbook-primary lg:size-[clamp(54px,4.6vw,70px)]"
      onClick={onClick}
    >
      <Image
        src={imageSrc}
        alt=""
        width={67}
        height={70}
        sizes="70px"
        className="h-full w-auto object-contain"
      />
      <span className="sr-only">{label}</span>
    </button>
  )
}

function SubmissionProgressBadge({
  className,
  submittedCount,
  totalCount,
}: {
  className?: string
  submittedCount: number
  totalCount: number
}) {
  if (totalCount <= 0) return null

  return (
    <p
      aria-live="polite"
      className={cn(
        'body-b mx-auto inline-flex min-h-9 items-center justify-center rounded-full border border-[#ffd2df] bg-white/92 px-4 text-[#db4d82] shadow-[0_8px_18px_rgb(129_89_54_/_12%)]',
        className,
      )}
    >
      제출 {submittedCount}/{totalCount}명
    </p>
  )
}
