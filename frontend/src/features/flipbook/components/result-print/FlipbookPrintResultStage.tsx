'use client'

import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import Image from 'next/image'
import { Play, Sparkles } from 'lucide-react'
import { motion } from 'motion/react'

import { cn } from '@/shared/libs'

import { useFlipbookPrintReveal } from '../../hooks'

export interface FlipbookPrintFrame {
  id: string
  title: string
  frameNumber: number
  imageUrl?: string | null
  accentColor?: string
  outputMode?: 'nemonic-print' | 'gif-playback'
}

export interface FlipbookPrintParticipant {
  id: string
  name: string
  firstStartedWorkId: string
  firstStartedWorkTitle: string
  accentColor?: string
  frames: FlipbookPrintFrame[]
}

interface FlipbookPrintResultStageProps {
  participants: FlipbookPrintParticipant[]
  activeParticipantIndex?: number
  className?: string
  printDurationMs?: number
  holdDurationMs?: number
  renderPaper?: (
    frame: FlipbookPrintFrame,
    frameIndex: number,
    participant: FlipbookPrintParticipant,
  ) => ReactNode
  onSelectParticipant?: (participantIndex: number) => void
}

const DEFAULT_ACCENT_COLORS = ['#f58c97', '#7ec6ad', '#f3c66f', '#96a8ee', '#c99be8', '#ef9a72']
const RESULT_STAGE_BACKGROUND_IMAGE_SRC = '/images/flipbook-result/figma-node-2826-background-render.png'
// const RESULT_STAGE_BACKGROUND_IMAGE_SRC = '/images/flipbook-lobby/background.png'


const RESULT_STAGE_BACKGROUND_IMAGE_WIDTH = 1920
const RESULT_STAGE_BACKGROUND_IMAGE_HEIGHT = 1080
const FURNITURE_IMAGE_SRC = '/images/flipbook-result/figma-node-2826-furniture-left.png'
const FURNITURE_IMAGE_WIDTH = 1536
const FURNITURE_IMAGE_HEIGHT = 1024
const BOARD_IMAGE_SRC = '/images/flipbook-result/figma-node-2826-board-v2.png'
const BOARD_IMAGE_WIDTH = 1113
const BOARD_IMAGE_HEIGHT = 744
const NEMONIC_DEVICE_IMAGE_SRC = '/images/flipbook-result/attached-nemonic-device-v3-hq.png'
const NEMONIC_DEVICE_IMAGE_WIDTH = 1551
const NEMONIC_DEVICE_IMAGE_HEIGHT = 1035
const NEMONIC_OUTPUT_SLOT_IMAGE_SRC = '/images/flipbook-result/figma-node-2826-output-slot.svg'
const STAGE_ASPECT_RATIO = '1920/1080'
const STAGE_HEIGHT_BY_VIEWPORT = 'min(100dvh,calc(100vw*9/16))'
const STAGE_WIDTH_BY_VIEWPORT = 'min(100vw,calc(100dvh*16/9))'
const BACKGROUND_IMAGE_WIDTH = '100%'
const BACKGROUND_IMAGE_HEIGHT = '100%'
const TOP_FURNITURE_LEFT = '17.43%'
const TOP_FURNITURE_TOP = '-5.33%'
const TOP_FURNITURE_WIDTH = '58.36%'
const TOP_FURNITURE_HEIGHT = '27.74%'
const LEFT_FURNITURE_LEFT = '-5.05%'
const LEFT_FURNITURE_TOP = '44.91%'
const LEFT_FURNITURE_WIDTH = '47.97%'
const LEFT_FURNITURE_HEIGHT = '62.5%'
const BOARD_LEFT = '30.89%'
const BOARD_TOP = '12.78%'
const BOARD_WIDTH = '57.97%'
const BOARD_HEIGHT = '69.44%'
const BOARD_IMAGE_LEFT = '0%'
const BOARD_CROP_WIDTH = '100%'
const PARTICIPANT_PANEL_IMAGE_SRC = '/images/flipbook-result/participant-panel-v2.png'
const PARTICIPANT_PANEL_LEFT = '6.67%'
const PARTICIPANT_PANEL_TOP = '13.7%'
const PARTICIPANT_PANEL_WIDTH = '19.43%'
const PARTICIPANT_PANEL_HEIGHT = '68.24%'
const ATTACHED_PAPER_LEFT = '35.05%'
const ATTACHED_PAPER_TOP = '19.44%'
const ATTACHED_PAPER_WIDTH = '44.27%'
const ATTACHED_PAPER_HEIGHT = '56.76%'
const OUTPUT_SLOT_LEFT = '82.42%'
const OUTPUT_SLOT_TOP = '67.16%'
const OUTPUT_SLOT_WIDTH = '11.54%'
const OUTPUT_SLOT_HEIGHT = '0.94%'
const NEMONIC_DEVICE_LEFT = '75.36%'
const NEMONIC_DEVICE_TOP = '61.02%'
const NEMONIC_DEVICE_WIDTH = '26.93%'
const NEMONIC_DEVICE_HEIGHT = '31.91%'
const SLOT_PAPER_LEFT = '82.55%'
const SLOT_PAPER_WIDTH = '11.41%'
const SLOT_PAPER_HEIGHT = '15.74%'
const SLOT_PAPER_HIDDEN_TOP = '67.16%'
const SLOT_OUTPUT_PAPER_TOP = '51.39%'
const SLOT_PRINT_MASK_HEIGHT = `calc(${SLOT_PAPER_HIDDEN_TOP} - ${SLOT_OUTPUT_PAPER_TOP})`
const PRINT_RISE_DURATION_RATIO = 0.5
const PRINT_AFTER_RISE_PAUSE_RATIO = 0.25
const PAPER_ATTACH_DURATION_RATIO = 0.36
const PRINTED_PAPER_SHADOW_CLASS =
  'shadow-[0_2px_0_rgba(120,74,35,0.08),0_8px_18px_rgba(72,43,18,0.22),0_18px_36px_rgba(72,43,18,0.18)]'

export default function FlipbookPrintResultStage({
  participants,
  activeParticipantIndex = 0,
  className,
  printDurationMs = 1700,
  holdDurationMs = 850,
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
  const activeFrame = printFrames[activeFrameIndex] ?? null
  const previousFrame = activeFrameIndex > 0 ? printFrames[activeFrameIndex - 1] : null
  const shouldPrintActiveFrame = activeFrame?.outputMode !== 'gif-playback'

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

  return (
    <section
      className={cn(
        'relative min-h-screen overflow-hidden bg-[#fff7ed] text-[#1f2b1f]',
        className,
      )}
    >
      <div
        className="absolute left-1/2 top-1/2 overflow-hidden -translate-x-1/2 -translate-y-1/2"
        style={{
          aspectRatio: STAGE_ASPECT_RATIO,
          height: STAGE_HEIGHT_BY_VIEWPORT,
          width: STAGE_WIDTH_BY_VIEWPORT,
        }}
      >
        <StageBackground />
        <FurnitureLayers />
        <BoardLayer />

        {activeFrameIndex > 0 && <FixedAttachedPaperShadow />}

        {previousFrame && selectedParticipant && (
          <div
            className="absolute z-30"
            style={{
              height: ATTACHED_PAPER_HEIGHT,
              left: ATTACHED_PAPER_LEFT,
              top: ATTACHED_PAPER_TOP,
              width: ATTACHED_PAPER_WIDTH,
            }}
          >
            <ShadowedPrintedPaper
              frame={previousFrame}
              frameIndex={activeFrameIndex - 1}
              participant={selectedParticipant}
              renderPaper={renderPaper}
            />
          </div>
        )}

        {activeFrame && selectedParticipant && shouldPrintActiveFrame && (
          <ActivePrintedPaper
            key={`${selectedParticipant.id}-${activeFrame.id}-${printCycleKey}`}
            frame={activeFrame}
            frameIndex={activeFrameIndex}
            participant={selectedParticipant}
            printDurationMs={printDurationMs}
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

        <NemonicDeviceImage isPrinting={isPlaying && !isComplete && shouldPrintActiveFrame} />

        <PrintOutputSlot />

        <aside
          className="absolute z-60 flex flex-col overflow-hidden rounded-[30px] px-5 pb-5 pt-4"
          style={{
            height: PARTICIPANT_PANEL_HEIGHT,
            left: PARTICIPANT_PANEL_LEFT,
            top: PARTICIPANT_PANEL_TOP,
            width: PARTICIPANT_PANEL_WIDTH,
          }}
        >
          <Image
            src={PARTICIPANT_PANEL_IMAGE_SRC}
            alt=""
            fill
            sizes="20vw"
            priority
            unoptimized
            className="pointer-events-none absolute inset-0 z-0 size-full object-fill"
          />

          <div className="relative z-10 flex min-h-[9%] items-start gap-3">
            <p className="h3-b flex items-center gap-2 text-[#5d3b38]">
              <Sparkles className="size-5 text-[#ffb84d]" aria-hidden />
              참여자 목록
            </p>
          </div>

          <div className="relative z-10 mt-4 grid flex-1 content-start gap-2 overflow-y-auto pr-1">
            {participants.map((participant, participantIndex) => {
              const isActive = participantIndex === normalizedSelectedParticipantIndex
              const accentColor = getParticipantAccentColor(participant, participantIndex)
              const thumbnailImageUrl = participant.frames.find((frame) => frame.imageUrl)?.imageUrl
              const printableFrameCount = getPrintableFrameCount(participant)
              const hasGifPlayback = participant.frames.some(
                (frame) => frame.outputMode === 'gif-playback',
              )

              return (
                <div
                  key={participant.id}
                  className={cn(
                    'group relative grid min-h-[68px] grid-cols-[54px_1fr_auto] items-center gap-2 overflow-hidden rounded-[8px] border border-white/80 bg-white/76 px-2.5 py-2 text-left shadow-[0_8px_18px_rgb(226_128_154_/_10%)] transition',
                    'hover:-translate-y-0.5 hover:bg-white hover:shadow-[0_12px_22px_rgb(226_128_154_/_18%)]',
                    isActive && 'border-[#ff8aa4] bg-white shadow-[0_12px_26px_rgb(226_128_154_/_24%)]',
                  )}
                >
                  <button
                    type="button"
                    onClick={() => selectParticipant(participantIndex)}
                    className="contents text-left"
                  >
                    <span className="relative h-[48px] w-[42px]" aria-hidden>
                      <span className="absolute left-0 top-1 size-[42px] rotate-[-8deg] rounded-[5px] border border-[#ffc1cf] bg-[#ffeef2]" />
                      <span className="absolute left-2 top-0 size-[42px] rotate-[5deg] rounded-[5px] border border-white bg-white shadow-[0_4px_10px_rgb(120_80_80_/_12%)]">
                        {thumbnailImageUrl ? (
                          <Image
                            src={thumbnailImageUrl}
                            alt=""
                            fill
                            sizes="42px"
                            unoptimized
                            className="rounded-[5px] object-cover p-1"
                          />
                        ) : (
                          <span className="absolute inset-1.5 rounded-[3px]" style={{ backgroundColor: `${accentColor}55` }} />
                        )}
                        <span className="absolute inset-x-2 bottom-1 h-1 rounded-full bg-white/80" />
                      </span>
                    </span>

                    <span className="min-w-0">
                      <span className="body-b block truncate text-[#332222]">{participant.name}</span>
                    </span>
                  </button>

                  <span className="relative z-10 grid justify-items-end gap-1">
                    <span className="caption-b text-[#e56883]">{printableFrameCount}장</span>
                    <button
                      type="button"
                      onClick={() => selectParticipantGif(participantIndex)}
                      disabled={!hasGifPlayback}
                      className="caption-b inline-flex h-6 items-center gap-1 rounded-full border border-[#ff9ab2]/70 bg-white/86 px-2 text-[#d9607a] shadow-[0_4px_8px_rgb(226_128_154_/_12%)] transition hover:bg-[#fff0f4] disabled:opacity-45"
                      aria-label={`${participant.name} GIF만 보기`}
                    >
                      <Play className="size-3 fill-[#d9607a]" aria-hidden />
                      GIF
                    </button>
                  </span>

                  <span
                    className={cn(
                      'pointer-events-none absolute inset-0 rounded-[8px] opacity-0 ring-2 ring-[#ff8aa4] transition',
                      isActive && 'opacity-100',
                    )}
                    aria-hidden
                  />
                </div>
              )
            })}
          </div>
        </aside>
      </div>
    </section>
  )
}

function FurnitureLayers() {
  return (
    <>
      <div
        className="pointer-events-none absolute z-[1] flex items-center justify-center"
        style={{
          height: TOP_FURNITURE_HEIGHT,
          left: TOP_FURNITURE_LEFT,
          top: TOP_FURNITURE_TOP,
          width: TOP_FURNITURE_WIDTH,
        }}
      >
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
      <div
        className="pointer-events-none absolute z-[1] overflow-hidden"
        style={{
          height: LEFT_FURNITURE_HEIGHT,
          left: LEFT_FURNITURE_LEFT,
          top: LEFT_FURNITURE_TOP,
          width: LEFT_FURNITURE_WIDTH,
        }}
      >
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

function BoardLayer() {
  return (
    <div
      className="pointer-events-none absolute z-10 overflow-hidden"
      style={{
        height: BOARD_HEIGHT,
        left: BOARD_LEFT,
        top: BOARD_TOP,
        width: BOARD_WIDTH,
      }}
    >
      <Image
        src={BOARD_IMAGE_SRC}
        alt=""
        width={BOARD_IMAGE_WIDTH}
        height={BOARD_IMAGE_HEIGHT}
        priority
        unoptimized
        className="absolute top-0 h-full max-w-none object-fill"
        style={{
          left: BOARD_IMAGE_LEFT,
          width: BOARD_CROP_WIDTH,
        }}
      />
    </div>
  )
}

function StageBackground() {
  return (
    <div
      className="pointer-events-none absolute left-0 top-0 z-0 overflow-hidden"
      style={{
        height: BACKGROUND_IMAGE_HEIGHT,
        width: BACKGROUND_IMAGE_WIDTH,
      }}
    >
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
    <div
      className="pointer-events-none absolute z-[90] rotate-[1.75deg] overflow-hidden"
      style={{
        height: OUTPUT_SLOT_HEIGHT,
        left: OUTPUT_SLOT_LEFT,
        top: OUTPUT_SLOT_TOP,
        width: OUTPUT_SLOT_WIDTH,
      }}
    >
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

function ActivePrintedPaper({
  frame,
  frameIndex,
  participant,
  printDurationMs,
  renderPaper,
}: {
  frame: FlipbookPrintFrame
  frameIndex: number
  participant: FlipbookPrintParticipant
  printDurationMs: number
  renderPaper?: (
    frame: FlipbookPrintFrame,
    frameIndex: number,
    participant: FlipbookPrintParticipant,
  ) => ReactNode
}) {
  const [printPhase, setPrintPhase] = useState<'slot' | 'expand'>('slot')
  const expandTimerRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (expandTimerRef.current !== null) {
        window.clearTimeout(expandTimerRef.current)
      }
    }
  }, [])

  const scheduleExpandAfterPrint = () => {
    if (expandTimerRef.current !== null) {
      window.clearTimeout(expandTimerRef.current)
    }

    expandTimerRef.current = window.setTimeout(() => {
      setPrintPhase('expand')
    }, printDurationMs * PRINT_AFTER_RISE_PAUSE_RATIO)
  }

  if (printPhase === 'slot') {
    return (
      <div
        className="pointer-events-none absolute z-[70] overflow-hidden"
        style={{
          height: SLOT_PRINT_MASK_HEIGHT,
          left: SLOT_PAPER_LEFT,
          top: SLOT_OUTPUT_PAPER_TOP,
          width: SLOT_PAPER_WIDTH,
        }}
      >
        <motion.div
          className="absolute inset-x-0 bottom-0 h-full"
          initial={{ y: '100%' }}
          animate={{ y: '0%' }}
          transition={{
            duration: printDurationMs * PRINT_RISE_DURATION_RATIO / 1000,
            ease: [0.12, 0.78, 0.16, 1],
          }}
          onAnimationComplete={scheduleExpandAfterPrint}
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

  return (
    <motion.div
      className="pointer-events-none absolute z-40"
      initial={{
        left: SLOT_PAPER_LEFT,
        top: SLOT_OUTPUT_PAPER_TOP,
        width: SLOT_PAPER_WIDTH,
        height: SLOT_PAPER_HEIGHT,
      }}
      animate={{
        left: ATTACHED_PAPER_LEFT,
        top: ATTACHED_PAPER_TOP,
        width: ATTACHED_PAPER_WIDTH,
        height: ATTACHED_PAPER_HEIGHT,
      }}
      transition={{
        duration: printDurationMs * PAPER_ATTACH_DURATION_RATIO / 1000,
        ease: [0.14, 0.84, 0.18, 1],
      }}
    >
      {frameIndex === 0 && (
        <motion.div
          className={cn('absolute inset-0', PRINTED_PAPER_SHADOW_CLASS)}
          initial={{ opacity: 0.78 }}
          animate={{ opacity: 1 }}
          transition={{
            duration: printDurationMs * PAPER_ATTACH_DURATION_RATIO / 1000,
            ease: [0.14, 0.84, 0.18, 1],
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
  renderPaper?: (
    frame: FlipbookPrintFrame,
    frameIndex: number,
    participant: FlipbookPrintParticipant,
  ) => ReactNode
}) {
  return (
    <motion.div
      className="pointer-events-none absolute z-40"
      initial={{ opacity: 0, scale: 0.97 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{
        duration: 0.34,
        ease: [0.14, 0.84, 0.18, 1],
      }}
      style={{
        height: ATTACHED_PAPER_HEIGHT,
        left: ATTACHED_PAPER_LEFT,
        top: ATTACHED_PAPER_TOP,
        width: ATTACHED_PAPER_WIDTH,
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
      className={cn('pointer-events-none absolute z-20', PRINTED_PAPER_SHADOW_CLASS)}
      style={{
        height: ATTACHED_PAPER_HEIGHT,
        left: ATTACHED_PAPER_LEFT,
        top: ATTACHED_PAPER_TOP,
        width: ATTACHED_PAPER_WIDTH,
      }}
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
  renderPaper?: (
    frame: FlipbookPrintFrame,
    frameIndex: number,
    participant: FlipbookPrintParticipant,
  ) => ReactNode
}) {
  return (
    <div className="relative h-full w-full">
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
  renderPaper?: (
    frame: FlipbookPrintFrame,
    frameIndex: number,
    participant: FlipbookPrintParticipant,
  ) => ReactNode
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

function NemonicDeviceImage({ isPrinting }: { isPrinting: boolean }) {
  return (
    <div
      className={cn(
        'absolute z-50',
        isPrinting && 'animate-[nemonic-device-hum_180ms_linear_infinite]',
      )}
      style={{
        height: NEMONIC_DEVICE_HEIGHT,
        left: NEMONIC_DEVICE_LEFT,
        top: NEMONIC_DEVICE_TOP,
        width: NEMONIC_DEVICE_WIDTH,
      }}
      aria-hidden
    >
      <Image
        src={NEMONIC_DEVICE_IMAGE_SRC}
        alt=""
        width={NEMONIC_DEVICE_IMAGE_WIDTH}
        height={NEMONIC_DEVICE_IMAGE_HEIGHT}
        priority
        unoptimized
        className="size-full max-w-none object-cover"
      />
    </div>
  )
}

function getParticipantAccentColor(participant: FlipbookPrintParticipant, participantIndex: number) {
  return participant.accentColor ?? DEFAULT_ACCENT_COLORS[participantIndex % DEFAULT_ACCENT_COLORS.length]
}

function getPrintableFrameCount(participant: FlipbookPrintParticipant) {
  return participant.frames.filter((frame) => frame.outputMode !== 'gif-playback').length
}

function getFrameAccentColor(frame: FlipbookPrintFrame, frameIndex: number, fallbackColor?: string) {
  return frame.accentColor ?? fallbackColor ?? DEFAULT_ACCENT_COLORS[frameIndex % DEFAULT_ACCENT_COLORS.length]
}
