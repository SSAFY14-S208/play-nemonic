'use client'

import { useLayoutEffect, useRef } from 'react'
import { motion } from 'motion/react'

import { cn } from '@/shared/libs'
import { playBrowserAudio, preloadBrowserAudio } from '@/shared/utils'

import { FLIPBOOK_SOUND_PATHS } from '@/features/flipbook/constants'
import {
  DEFAULT_ACCENT_COLORS,
  PRINT_COMPLETE_SOUND_LEAD_MS,
  PRINT_COMPLETE_SOUND_OFFSET_SECONDS,
  PRINT_COMPLETE_SOUND_VOLUME,
  PRINT_RISE_DURATION_RATIO,
  PRINT_START_SOUND_OFFSET_SECONDS,
  PRINT_START_SOUND_VOLUME,
  PRINTED_PAPER_SHADOW_CLASS,
  resultPrintStageStyles as styles,
} from './constants'
import type {
  FlipbookPrintFrame,
  FlipbookPrintParticipant,
  RenderFlipbookPrintPaper,
} from './types'

interface PrintedPaperProps {
  frame: FlipbookPrintFrame
  frameIndex: number
  participant: FlipbookPrintParticipant
  renderPaper?: RenderFlipbookPrintPaper
}

interface SlotPrintedPaperProps extends PrintedPaperProps {
  printDurationMs: number
  onPrintRiseComplete: () => void
}

export function SlotPrintedPaper({
  frame,
  frameIndex,
  participant,
  printDurationMs,
  renderPaper,
  onPrintRiseComplete,
}: SlotPrintedPaperProps) {
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

export function AttachedPrintedPaper({
  frame,
  frameIndex,
  participant,
  renderPaper,
}: PrintedPaperProps) {
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

export function DirectPlaybackPaper({
  frame,
  frameIndex,
  participant,
  renderPaper,
}: PrintedPaperProps) {
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

export function FixedAttachedPaperShadow() {
  return (
    <div
      className={cn(styles.attachedPaperFrame, 'pointer-events-none z-20', PRINTED_PAPER_SHADOW_CLASS)}
      aria-hidden
    />
  )
}

export function ShadowedPrintedPaper({
  frame,
  frameIndex,
  participant,
  renderPaper,
}: PrintedPaperProps) {
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
}: PrintedPaperProps) {
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
}: Omit<PrintedPaperProps, 'renderPaper'>) {
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

function getFrameAccentColor(frame: FlipbookPrintFrame, frameIndex: number, fallbackColor?: string) {
  return frame.accentColor ?? fallbackColor ?? DEFAULT_ACCENT_COLORS[frameIndex % DEFAULT_ACCENT_COLORS.length]
}
