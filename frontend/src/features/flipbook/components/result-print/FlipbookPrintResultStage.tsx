'use client'

import { useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import Image from 'next/image'
import { motion } from 'motion/react'

import { useNemonicPrintVibration } from '@/shared/hooks'
import { cn } from '@/shared/libs'
import { playBrowserAudio, preloadBrowserAudio } from '@/shared/utils'

import { FLIPBOOK_SOUND_PATHS } from '@/features/flipbook/constants'
import {
  ARTIST_BADGE_IMAGE_SRC,
  BOARD_IMAGE_HEIGHT,
  BOARD_IMAGE_SRC,
  BOARD_IMAGE_WIDTH,
  DEFAULT_ACCENT_COLORS,
  FURNITURE_IMAGE_HEIGHT,
  FURNITURE_IMAGE_SRC,
  FURNITURE_IMAGE_WIDTH,
  NEMONIC_DEVICE_IMAGE_HEIGHT,
  NEMONIC_DEVICE_IMAGE_SRC,
  NEMONIC_DEVICE_IMAGE_WIDTH,
  NEMONIC_OUTPUT_SLOT_IMAGE_SRC,
  PARTICIPANT_PANEL_IMAGE_SRC,
  PRINT_AFTER_RISE_PAUSE_RATIO,
  PRINT_COMPLETE_SOUND_LEAD_MS,
  PRINT_COMPLETE_SOUND_OFFSET_SECONDS,
  PRINT_COMPLETE_SOUND_VOLUME,
  PRINT_RISE_DURATION_RATIO,
  PRINT_START_SOUND_OFFSET_SECONDS,
  PRINT_START_SOUND_VOLUME,
  PRINTED_PAPER_SHADOW_CLASS,
  RESULT_STAGE_BACKGROUND_IMAGE_HEIGHT,
  RESULT_STAGE_BACKGROUND_IMAGE_SRC,
  RESULT_STAGE_BACKGROUND_IMAGE_WIDTH,
  SKIP_BUTTON_IMAGE_SRC,
  resultPrintStageStyles as styles,
} from './constants'
import { useFlipbookPrintReveal } from './hooks'
import { ParticipantListPanel } from './ParticipantListPanel'
import type {
  FlipbookPrintFrame,
  FlipbookPrintParticipant,
  RenderFlipbookPrintPaper,
} from './types'
import './FlipbookPrintResultStage.css'

interface FlipbookPrintResultStageProps {
  participants: FlipbookPrintParticipant[]
  activeParticipantIndex?: number
  className?: string
  printDurationMs?: number
  holdDurationMs?: number
  renderPaper?: RenderFlipbookPrintPaper
  onSelectParticipant?: (participantIndex: number) => void
  // 참여자의 출력(print) 시퀀스가 끝나고 사용자가 결과(보통 GIF)를 보고 있는
  // 시점에 참여자당 1회 발사. 부모에서 자동 전환 타이머의 시작점으로 사용한다.
  // - gif-playback 프레임이 있는 경우: active frame이 gif-playback이 되는 즉시
  // - 없는 경우: 모든 print 프레임 출력이 완전히 끝난 시점
  onParticipantRevealComplete?: (participantIndex: number) => void
}

export default function FlipbookPrintResultStage({
  participants,
  activeParticipantIndex = 0,
  className,
  printDurationMs = 1700,
  holdDurationMs = 850,
  onParticipantRevealComplete,
  renderPaper,
  onSelectParticipant,
}: FlipbookPrintResultStageProps) {
  const [selectedParticipantIndex, setSelectedParticipantIndex] = useState(activeParticipantIndex)
  const normalizedSelectedParticipantIndex = Math.min(
    Math.max(0, selectedParticipantIndex),
    Math.max(0, participants.length - 1),
  )
  const selectedParticipant = participants[normalizedSelectedParticipantIndex] ?? null
  const printFrames = useMemo(
    () => selectedParticipant?.frames ?? [],
    [selectedParticipant],
  )
  const {
    activeFrameIndex,
    isPlaying,
    isComplete,
    printCycleKey,
    replay,
    showFrame,
  } = useFlipbookPrintReveal({
    frameCount: printFrames.length,
    printDurationMs,
    holdDurationMs,
  })
  const [pendingGifParticipantIndex, setPendingGifParticipantIndex] = useState<number | null>(null)
  const [printPhaseState, setPrintPhaseState] = useState<{
    activePrintKey: string | null
    phase: 'slot' | 'attach'
  }>({
    activePrintKey: null,
    phase: 'slot',
  })
  const expandTimerRef = useRef<number | null>(null)
  const activeFrame = printFrames[activeFrameIndex] ?? null
  const previousFrame = activeFrameIndex > 0 ? printFrames[activeFrameIndex - 1] : null
  const shouldPrintActiveFrame = activeFrame?.outputMode !== 'gif-playback'
  const activePrintKey = activeFrame && selectedParticipant
    ? `${selectedParticipant.id}-${activeFrame.id}-${printCycleKey}`
    : null
  const effectivePrintPhase =
    printPhaseState.activePrintKey === activePrintKey ? printPhaseState.phase : 'slot'
  const attachedBoardFrame =
    activeFrame && !shouldPrintActiveFrame
      ? activeFrame
      : activeFrame && effectivePrintPhase === 'attach'
        ? activeFrame
        : previousFrame
  // 인쇄 애니메이션 진행 중에만 디바이스 진동을 활성. NemonicDeviceImage의
  // isPrinting과 동일 조건을 유지.
  useNemonicPrintVibration(isPlaying && !isComplete && shouldPrintActiveFrame)
  const selectedParticipantHasGifPlayback = selectedParticipant?.frames.some(
    (frame) => frame.outputMode === 'gif-playback',
  ) ?? false

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (!cancelled) {
        setSelectedParticipantIndex(activeParticipantIndex)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activeParticipantIndex])

  // 참여자별 reveal 완료 콜백 — 자동 전환을 위해 부모(FlipbookPage)에 신호.
  // 같은 참여자에 대해 중복 발사되지 않도록 lastFiredRevealIndexRef로 가드한다.
  // 참여자 인덱스가 바뀌면 ref를 null로 리셋해 새 참여자에 대해 다시 발사 가능.
  const lastFiredRevealIndexRef = useRef<number | null>(null)
  useEffect(() => {
    lastFiredRevealIndexRef.current = null
  }, [normalizedSelectedParticipantIndex])

  useEffect(() => {
    if (!onParticipantRevealComplete) return
    // 두 가지 reveal 완료 조건 중 빠른 것을 사용:
    // 1) 활성 프레임이 gif-playback (출력 시퀀스가 GIF로 전환된 순간)
    // 2) gif-playback 프레임이 없는 결과의 경우 print 시퀀스 자체가 모두 끝난
    //    시점 (isComplete + !isPlaying)
    const isShowingGifPlaybackFrame =
      activeFrame !== null && activeFrame.outputMode === 'gif-playback'
    const isPrintSequenceFullyDone = isComplete && !isPlaying
    if (!isShowingGifPlaybackFrame && !isPrintSequenceFullyDone) return
    if (lastFiredRevealIndexRef.current === normalizedSelectedParticipantIndex) return
    lastFiredRevealIndexRef.current = normalizedSelectedParticipantIndex

    let cancelled = false
    void (async () => {
      if (!cancelled) onParticipantRevealComplete(normalizedSelectedParticipantIndex)
    })()
    return () => {
      cancelled = true
    }
  }, [
    activeFrame,
    isComplete,
    isPlaying,
    normalizedSelectedParticipantIndex,
    onParticipantRevealComplete,
  ])

  const selectParticipant = (participantIndex: number) => {
    setPendingGifParticipantIndex(null)
    setSelectedParticipantIndex(participantIndex)
    onSelectParticipant?.(participantIndex)
    replay()
  }

  const selectParticipantGif = (participantIndex: number) => {
    const targetParticipant = participants[participantIndex]
    const hasGifPlayback = targetParticipant?.frames.some(
      (frame) => frame.outputMode === 'gif-playback',
    )
    if (!hasGifPlayback) return

    setSelectedParticipantIndex(participantIndex)
    onSelectParticipant?.(participantIndex)
    setPendingGifParticipantIndex(participantIndex)
  }

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (pendingGifParticipantIndex !== normalizedSelectedParticipantIndex) return

      const gifFrameIndex = printFrames.findIndex(
        (frame) => frame.outputMode === 'gif-playback',
      )
      if (gifFrameIndex < 0) {
        if (!cancelled) {
          setPendingGifParticipantIndex(null)
        }
        return
      }

      if (!cancelled) {
        showFrame(gifFrameIndex)
        setPendingGifParticipantIndex(null)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [normalizedSelectedParticipantIndex, pendingGifParticipantIndex, printFrames, showFrame])

  useEffect(() => {
    let cancelled = false

    void (async () => {
      if (expandTimerRef.current !== null) {
        window.clearTimeout(expandTimerRef.current)
        expandTimerRef.current = null
      }

      if (!cancelled) {
        setPrintPhaseState({
          activePrintKey,
          phase: 'slot',
        })
      }
    })()

    return () => {
      cancelled = true
    }
  }, [activePrintKey])

  useEffect(() => {
    return () => {
      if (expandTimerRef.current !== null) {
        window.clearTimeout(expandTimerRef.current)
      }
    }
  }, [])

  const scheduleAttachAfterPrint = () => {
    if (expandTimerRef.current !== null) {
      window.clearTimeout(expandTimerRef.current)
    }

    expandTimerRef.current = window.setTimeout(() => {
      setPrintPhaseState({
        activePrintKey,
        phase: 'attach',
      })
    }, printDurationMs * PRINT_AFTER_RISE_PAUSE_RATIO)
  }

  return (
    <section
      className={cn(
        styles.stage,
        className,
      )}
    >
      <div className={styles.scene}>
        <StageBackground />
        <FurnitureLayers />

        <BoardLayer>
          {activeFrameIndex > 0 && <FixedAttachedPaperShadow />}

          {previousFrame && selectedParticipant && (
            <ShadowedPrintedPaper
              frame={previousFrame}
              frameIndex={activeFrameIndex - 1}
              participant={selectedParticipant}
              renderPaper={renderPaper}
            />
          )}

          {activeFrame && selectedParticipant && shouldPrintActiveFrame && effectivePrintPhase === 'attach' && (
            <AttachedPrintedPaper
              key={`${activePrintKey}-attach`}
              frame={activeFrame}
              frameIndex={activeFrameIndex}
              participant={selectedParticipant}
              renderPaper={renderPaper}
            />
          )}

          {activeFrame && selectedParticipant && !shouldPrintActiveFrame && (
            <DirectPlaybackPaper
              key={`${selectedParticipant.id}-${activeFrame.id}-${printCycleKey}`}
              frame={activeFrame}
              frameIndex={activeFrameIndex}
              participant={selectedParticipant}
              renderPaper={renderPaper}
            />
          )}

          <SkipPlaybackButton
            disabled={!selectedParticipantHasGifPlayback}
            onClick={() => selectParticipantGif(normalizedSelectedParticipantIndex)}
          />

          {attachedBoardFrame && selectedParticipant && (
            <FrameArtistBadge
              frame={attachedBoardFrame}
              participant={selectedParticipant}
            />
          )}
        </BoardLayer>

        <NemonicDeviceImage isPrinting={isPlaying && !isComplete && shouldPrintActiveFrame}>
          {activeFrame && selectedParticipant && shouldPrintActiveFrame && effectivePrintPhase === 'slot' && (
            <SlotPrintedPaper
              key={`${activePrintKey}-slot`}
              frame={activeFrame}
              frameIndex={activeFrameIndex}
              participant={selectedParticipant}
              printDurationMs={printDurationMs}
              renderPaper={renderPaper}
              onPrintRiseComplete={scheduleAttachAfterPrint}
            />
          )}
        </NemonicDeviceImage>

        <aside className={styles.participantPanel}>
          <Image
            src={PARTICIPANT_PANEL_IMAGE_SRC}
            alt=""
            fill
            sizes="20vw"
            priority
            unoptimized
            className={cn(
              styles.participantPanelImage,
              'pointer-events-none absolute inset-0 z-0 size-full object-fill',
            )}
          />

          <ParticipantListPanel
            participants={participants}
            selectedParticipantIndex={normalizedSelectedParticipantIndex}
            onSelectParticipant={selectParticipant}
          />
        </aside>
      </div>
    </section>
  )
}

function SkipPlaybackButton({
  disabled,
  onClick,
}: {
  disabled: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      className={styles.skipPlaybackButton}
      aria-label="현재 플립북 GIF 장면으로 건너뛰기"
      title="skip"
      onClick={onClick}
    >
      <Image
        src={SKIP_BUTTON_IMAGE_SRC}
        alt=""
        fill
        sizes="86px"
        unoptimized
        draggable={false}
        className={styles.skipPlaybackButtonImage}
        aria-hidden
      />
      <span className={styles.skipPlaybackButtonText}>
      skip
      </span>
    </button>
  )
}

function FrameArtistBadge({
  frame,
  participant,
}: {
  frame: FlipbookPrintFrame
  participant: FlipbookPrintParticipant
}) {
  const isGifPlaybackFrame = frame.outputMode === 'gif-playback'
  const artistName = isGifPlaybackFrame
    ? participant.name
    : frame.drawnByName?.trim() || participant.name
  const labelText = isGifPlaybackFrame ? '완성본' : '그린 사람'

  return (
    <div className={styles.frameArtistBadge} aria-live="polite">
      <Image
        src={ARTIST_BADGE_IMAGE_SRC}
        alt=""
        fill
        sizes="150px"
        unoptimized
        draggable={false}
        className={styles.frameArtistBadgeImage}
        aria-hidden
      />
      <span className={styles.frameArtistBadgeLabel}>{labelText}</span>
      <span className={styles.frameArtistBadgeName}>{artistName}</span>
    </div>
  )
}

function FurnitureLayers() {
  return (
    <>
      <div className={styles.topFurniture}>
        <div className="relative h-[79.46%] w-[98.97%] rotate-[3.2deg] overflow-hidden">
          <Image
            src={FURNITURE_IMAGE_SRC}
            alt=""
            width={FURNITURE_IMAGE_WIDTH}
            height={FURNITURE_IMAGE_HEIGHT}
            priority
            unoptimized
            className="absolute left-[-36.96%] top-[-33.16%] h-[593.26%] w-[191.05%] max-w-none object-fill"
          />
        </div>
      </div>
      <div className={styles.leftFurniture}>
        <Image
          src={FURNITURE_IMAGE_SRC}
          alt=""
          width={FURNITURE_IMAGE_WIDTH}
          height={FURNITURE_IMAGE_HEIGHT}
          priority
          unoptimized
          className="absolute left-[-0.03%] top-[-81.75%] h-[181.75%] w-[199.94%] max-w-none object-fill"
        />
      </div>
    </>
  )
}

function BoardLayer({ children }: { children: ReactNode }) {
  return (
    <div className={styles.boardLayer}>
      <Image
        src={BOARD_IMAGE_SRC}
        alt=""
        width={BOARD_IMAGE_WIDTH}
        height={BOARD_IMAGE_HEIGHT}
        priority
        unoptimized
        className={styles.boardImage}
      />
      {children}
    </div>
  )
}

function StageBackground() {
  return (
    <div className={styles.background}>
      <Image
        src={RESULT_STAGE_BACKGROUND_IMAGE_SRC}
        alt=""
        width={RESULT_STAGE_BACKGROUND_IMAGE_WIDTH}
        height={RESULT_STAGE_BACKGROUND_IMAGE_HEIGHT}
        priority
        unoptimized
        className="h-full w-full max-w-none object-cover"
      />
    </div>
  )
}

function PrintOutputSlot() {
  return (
    <div className={styles.outputSlot}>
      <Image
        src={NEMONIC_OUTPUT_SLOT_IMAGE_SRC}
        alt=""
        fill
        sizes="12vw"
        unoptimized
        className="size-full max-w-none object-fill drop-shadow-[0_2px_2px_rgba(68,29,0,0.16)]"
      />
    </div>
  )
}

function SlotPrintedPaper({
  frame,
  frameIndex,
  participant,
  printDurationMs,
  renderPaper,
  onPrintRiseComplete,
}: {
  frame: FlipbookPrintFrame
  frameIndex: number
  participant: FlipbookPrintParticipant
  printDurationMs: number
  renderPaper?: RenderFlipbookPrintPaper
  onPrintRiseComplete: () => void
}) {
  const printCompleteSoundTimerRef = useRef<number | null>(null)

  useLayoutEffect(() => {
    preloadBrowserAudio(FLIPBOOK_SOUND_PATHS.print, PRINT_START_SOUND_VOLUME)
    preloadBrowserAudio(FLIPBOOK_SOUND_PATHS.cut, PRINT_COMPLETE_SOUND_VOLUME)
    playBrowserAudio(
      FLIPBOOK_SOUND_PATHS.print,
      PRINT_START_SOUND_VOLUME,
      PRINT_START_SOUND_OFFSET_SECONDS,
    )

    if (printCompleteSoundTimerRef.current !== null) {
      window.clearTimeout(printCompleteSoundTimerRef.current)
    }

    printCompleteSoundTimerRef.current = window.setTimeout(
      () => {
        playBrowserAudio(
          FLIPBOOK_SOUND_PATHS.cut,
          PRINT_COMPLETE_SOUND_VOLUME,
          PRINT_COMPLETE_SOUND_OFFSET_SECONDS,
        )
      },
      Math.max(
        printDurationMs * PRINT_RISE_DURATION_RATIO - PRINT_COMPLETE_SOUND_LEAD_MS,
        0,
      ),
    )

    return () => {
      if (printCompleteSoundTimerRef.current !== null) {
        window.clearTimeout(printCompleteSoundTimerRef.current)
      }
    }
  }, [printDurationMs])

  return (
    <div className={styles.slotPrintMask}>
      <motion.div
        className="absolute inset-x-0 bottom-0 h-full"
        initial={{ y: '100%' }}
        animate={{ y: '0%' }}
        transition={{
          duration: printDurationMs * PRINT_RISE_DURATION_RATIO / 1000,
          ease: [0.12, 0.78, 0.16, 1],
        }}
        onAnimationComplete={onPrintRiseComplete}
      >
        {frameIndex === 0 && (
          <motion.div
            className={cn('absolute inset-0 origin-bottom', PRINTED_PAPER_SHADOW_CLASS)}
            initial={{ opacity: 0.18, scaleY: 0.04 }}
            animate={{ opacity: 0.78, scaleY: 1 }}
            transition={{
              duration: printDurationMs * PRINT_RISE_DURATION_RATIO / 1000,
              ease: [0.12, 0.78, 0.16, 1],
            }}
            aria-hidden
          />
        )}
        <PrintedPaper
          frame={frame}
          frameIndex={frameIndex}
          participant={participant}
          renderPaper={renderPaper}
        />
      </motion.div>
    </div>
  )
}

function AttachedPrintedPaper({
  frame,
  frameIndex,
  participant,
  renderPaper,
}: {
  frame: FlipbookPrintFrame
  frameIndex: number
  participant: FlipbookPrintParticipant
  renderPaper?: RenderFlipbookPrintPaper
}) {
  return (
    <motion.div
      className={cn(styles.attachedPaperFrame, 'pointer-events-none')}
      initial={{ opacity: 0, scale: 0.92 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{
        duration: 0.34,
        ease: [0.14, 0.84, 0.18, 1],
      }}
    >
      {frameIndex === 0 && (
        <motion.div
          className={cn('absolute inset-0', PRINTED_PAPER_SHADOW_CLASS)}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.22 }}
          aria-hidden
        />
      )}
      <PrintedPaper
        frame={frame}
        frameIndex={frameIndex}
        participant={participant}
        renderPaper={renderPaper}
      />
    </motion.div>
  )
}

function DirectPlaybackPaper({
  frame,
  frameIndex,
  participant,
  renderPaper,
}: {
  frame: FlipbookPrintFrame
  frameIndex: number
  participant: FlipbookPrintParticipant
  renderPaper?: RenderFlipbookPrintPaper
}) {
  return (
    <motion.div
      className={cn(styles.attachedPaperFrame, 'pointer-events-none')}
      initial={{ opacity: 0, scale: 0.97 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{
        duration: 0.34,
        ease: [0.14, 0.84, 0.18, 1],
      }}
    >
      {frameIndex === 0 && (
        <motion.div
          className={cn('absolute inset-0', PRINTED_PAPER_SHADOW_CLASS)}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.22 }}
          aria-hidden
        />
      )}
      <PrintedPaper
        frame={frame}
        frameIndex={frameIndex}
        participant={participant}
        renderPaper={renderPaper}
      />
    </motion.div>
  )
}

function FixedAttachedPaperShadow() {
  return (
    <div
      className={cn(styles.attachedPaperFrame, 'pointer-events-none z-20', PRINTED_PAPER_SHADOW_CLASS)}
      aria-hidden
    />
  )
}

function ShadowedPrintedPaper({
  frame,
  frameIndex,
  participant,
  renderPaper,
}: {
  frame: FlipbookPrintFrame
  frameIndex: number
  participant: FlipbookPrintParticipant
  renderPaper?: RenderFlipbookPrintPaper
}) {
  return (
    <div className={cn(styles.attachedPaperFrame, 'z-30')}>
      <PrintedPaper
        frame={frame}
        frameIndex={frameIndex}
        participant={participant}
        renderPaper={renderPaper}
      />
    </div>
  )
}

function PrintedPaper({
  frame,
  frameIndex,
  participant,
  renderPaper,
}: {
  frame: FlipbookPrintFrame
  frameIndex: number
  participant: FlipbookPrintParticipant
  renderPaper?: RenderFlipbookPrintPaper
}) {
  return (
    <article className="relative h-full w-full overflow-hidden border border-[#eadfd2]/90 bg-white ring-1 ring-white/70">
      {renderPaper ? (
        renderPaper(frame, frameIndex, participant)
      ) : (
        <BlankPaperPreview frame={frame} frameIndex={frameIndex} participant={participant} />
      )}
    </article>
  )
}

function BlankPaperPreview({
  frame,
  frameIndex,
  participant,
}: {
  frame: FlipbookPrintFrame
  frameIndex: number
  participant: FlipbookPrintParticipant
}) {
  const accentColor = getFrameAccentColor(frame, frameIndex, participant.accentColor)

  return (
    <div className="relative grid h-full place-items-center bg-[#fffefa]">
      <div
        className="absolute inset-x-0 top-0 h-2"
        style={{ backgroundColor: accentColor }}
      />
      <div className="grid justify-items-center gap-3">
        <span
          className="grid size-14 place-items-center rounded-full text-[18px] font-bold text-white shadow-[0_8px_16px_rgb(40_40_40_/_12%)]"
          style={{ backgroundColor: accentColor }}
        >
          {frame.frameNumber}
        </span>
        <div className="text-center">
          <p className="h3-b">{frame.title}</p>
          <p className="body-m mt-1 text-[#6c7b67]">{participant.name}</p>
        </div>
      </div>
      <div className="absolute bottom-5 left-6 right-6 grid gap-2 opacity-50">
        <span className="h-2 rounded-full bg-[#e3eadf]" />
        <span className="h-2 w-4/5 rounded-full bg-[#e3eadf]" />
      </div>
    </div>
  )
}

function NemonicDeviceImage({
  children,
  isPrinting,
}: {
  children?: ReactNode
  isPrinting: boolean
}) {
  return (
    <div
      className={cn(
        styles.printerLayer,
        isPrinting && 'animate-[nemonic-device-hum_180ms_linear_infinite]',
      )}
      aria-hidden
    >
      {children}
      <Image
        src={NEMONIC_DEVICE_IMAGE_SRC}
        alt=""
        width={NEMONIC_DEVICE_IMAGE_WIDTH}
        height={NEMONIC_DEVICE_IMAGE_HEIGHT}
        priority
        unoptimized
        className={styles.deviceImage}
      />
      <PrintOutputSlot />
    </div>
  )
}

function getFrameAccentColor(frame: FlipbookPrintFrame, frameIndex: number, fallbackColor?: string) {
  return frame.accentColor ?? fallbackColor ?? DEFAULT_ACCENT_COLORS[frameIndex % DEFAULT_ACCENT_COLORS.length]
}
