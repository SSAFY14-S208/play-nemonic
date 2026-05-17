'use client'

import { useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import { X } from 'lucide-react'
import { motion } from 'motion/react'

import { PostItNote } from '@/shared/components/PostItNote'
import { useNemonicPrintVibration } from '@/shared/hooks'
import { cn } from '@/shared/libs'
import { playBrowserAudio, preloadBrowserAudio } from '@/shared/utils'
import type { CommunityPendingMemoPlacement } from '../hooks'

interface CommunityMemoPrintRevealOverlayProps {
  placement: CommunityPendingMemoPlacement | null
  onAccept: () => void
  onCancel: () => void
}

const NEMONIC_DEVICE_IMAGE_SRC = '/images/flipbook-result/attached-nemonic-device-v3-hq.png'
const NEMONIC_OUTPUT_SLOT_IMAGE_SRC = '/images/flipbook-result/figma-node-2826-output-slot.svg'
const NEMONIC_DEVICE_IMAGE_WIDTH = 1551
const NEMONIC_DEVICE_IMAGE_HEIGHT = 1035
const NEMONIC_OUTPUT_SLOT_IMAGE_WIDTH = 224.578
const NEMONIC_OUTPUT_SLOT_IMAGE_HEIGHT = 6.41341
const DEVICE_REVEAL_DURATION_SECONDS = 3.05
const DEVICE_VERTICAL_OFFSET = 190
const DEVICE_REVEAL_TIMES = [0, 0.14, 0.78, 1]
const DEVICE_REVEAL_EASE = [0.16, 0.78, 0.18, 1] as const
const PAPER_PRINT_START_DELAY_SECONDS = DEVICE_REVEAL_DURATION_SECONDS * 0.14
const PAPER_PRINT_DURATION_SECONDS = 1.36
const PAPER_EXPAND_DURATION_SECONDS = 0.84
const PAPER_EXPAND_DELAY_MS = 140
const PAPER_EASE = [0.12, 0.78, 0.16, 1] as const
const MEMO_PRINT_START_SOUND_PATH = '/sounds/print_label.mp3'
const MEMO_PRINT_COMPLETE_SOUND_PATH = '/sounds/cut_label.mp3'
const MEMO_PRINT_START_SOUND_VOLUME = 0.36
const MEMO_PRINT_COMPLETE_SOUND_VOLUME = 0.42
const MEMO_PRINT_COMPLETE_SOUND_LEAD_SECONDS = 0.12
const MEMO_PRINT_COMPLETE_SOUND_OFFSET_SECONDS = 0.08
const PRINTER_SLOT_LEFT_RATIO = 0.262
const PRINTER_SLOT_TOP_RATIO = 0.192
const PRINTER_SLOT_WIDTH_RATIO = 0.429
const OUTPUT_SLOT_HEIGHT_BY_WIDTH =
  NEMONIC_OUTPUT_SLOT_IMAGE_HEIGHT / NEMONIC_OUTPUT_SLOT_IMAGE_WIDTH

type PrinterSlotMetrics = {
  left: number
  top: number
  width: number
  height: number
  centerX: number
  outputBaselineY: number
}

type ViewportMetrics = {
  centerX: number
  centerY: number
  width: number
  height: number
}

type PrintPhase = 'printing' | 'expanding'

export function CommunityMemoPrintRevealOverlay({
  placement,
  onAccept,
  onCancel,
}: CommunityMemoPrintRevealOverlayProps) {
  if (!placement) return null

  return (
    <CommunityMemoPrintRevealScene
      key={placement.previewUrl}
      placement={placement}
      onAccept={onAccept}
      onCancel={onCancel}
    />
  )
}

function CommunityMemoPrintRevealScene({
  placement,
  onAccept,
  onCancel,
}: {
  placement: CommunityPendingMemoPlacement
  onAccept: () => void
  onCancel: () => void
}) {
  const printerDeviceRef = useRef<HTMLDivElement>(null)
  const expandTimerRef = useRef<number | null>(null)
  const [printPhase, setPrintPhase] = useState<PrintPhase>('printing')
  const [isPrintedMemoReady, setPrintedMemoReady] = useState(false)
  const [printerSlotMetrics, setPrinterSlotMetrics] = useState<PrinterSlotMetrics | null>(null)
  const [viewportMetrics, setViewportMetrics] = useState<ViewportMetrics | null>(null)

  // 인쇄 단계 동안 디바이스 진동. 'expanding' 단계로 넘어가면 자동 정지.
  useNemonicPrintVibration(printPhase === 'printing')

  useEffect(() => {
    const handleEscapeKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCancel()
      }
    }

    window.addEventListener('keydown', handleEscapeKey)
    return () => window.removeEventListener('keydown', handleEscapeKey)
  }, [onCancel])

  useEffect(() => {
    return () => {
      if (expandTimerRef.current !== null) {
        window.clearTimeout(expandTimerRef.current)
      }
    }
  }, [])

  useEffect(() => {
    preloadBrowserAudio(MEMO_PRINT_START_SOUND_PATH, MEMO_PRINT_START_SOUND_VOLUME)
    preloadBrowserAudio(MEMO_PRINT_COMPLETE_SOUND_PATH, MEMO_PRINT_COMPLETE_SOUND_VOLUME)
  }, [])

  useEffect(() => {
    let animationFrameId: number | null = null

    const measurePrinterSlot = () => {
      const printerDeviceRect = printerDeviceRef.current?.getBoundingClientRect()
      if (!printerDeviceRect) return

      const slotLeft = printerDeviceRect.left + printerDeviceRect.width * PRINTER_SLOT_LEFT_RATIO
      const slotTop = printerDeviceRect.top + printerDeviceRect.height * PRINTER_SLOT_TOP_RATIO
      const slotWidth = printerDeviceRect.width * PRINTER_SLOT_WIDTH_RATIO
      const slotHeight = Math.max(slotWidth * OUTPUT_SLOT_HEIGHT_BY_WIDTH, 5)

      setPrinterSlotMetrics({
        left: slotLeft,
        top: slotTop,
        width: slotWidth,
        height: slotHeight,
        centerX: slotLeft + slotWidth / 2,
        outputBaselineY: slotTop,
      })
      setViewportMetrics({
        centerX: window.innerWidth / 2,
        centerY: window.innerHeight / 2,
        width: window.innerWidth,
        height: window.innerHeight,
      })
    }

    const schedulePrinterSlotMeasure = () => {
      if (animationFrameId !== null) {
        window.cancelAnimationFrame(animationFrameId)
      }

      animationFrameId = window.requestAnimationFrame(measurePrinterSlot)
    }

    schedulePrinterSlotMeasure()
    window.addEventListener('resize', schedulePrinterSlotMeasure)
    window.visualViewport?.addEventListener('resize', schedulePrinterSlotMeasure)

    return () => {
      if (animationFrameId !== null) {
        window.cancelAnimationFrame(animationFrameId)
      }

      window.removeEventListener('resize', schedulePrinterSlotMeasure)
      window.visualViewport?.removeEventListener('resize', schedulePrinterSlotMeasure)
    }
  }, [])

  const centerMemoSize = viewportMetrics ? getCenterMemoSize(viewportMetrics.width) : 0
  const slotMemoSize =
    printerSlotMetrics && viewportMetrics
      ? getSlotMemoSize(printerSlotMetrics.width, centerMemoSize)
      : 0

  const scheduleExpandPhase = () => {
    if (expandTimerRef.current !== null) {
      window.clearTimeout(expandTimerRef.current)
    }

    expandTimerRef.current = window.setTimeout(() => {
      setPrintPhase('expanding')
    }, PAPER_EXPAND_DELAY_MS)
  }

  return (
    <div className="fixed inset-0 z-[13000] overflow-hidden">
      <motion.div
        className="absolute inset-0 bg-[#19172a]/32 backdrop-blur-[2px]"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.22 }}
        aria-hidden
      />

      <button
        type="button"
        aria-label="Cancel print"
        onClick={onCancel}
        className="absolute right-[calc(1rem+env(safe-area-inset-right))] top-[calc(1rem+env(safe-area-inset-top))] z-[70] grid size-11 place-items-center rounded-full border border-border-default bg-surface-default/90 text-fg-secondary shadow-[0_10px_24px_rgb(34_26_52_/_18%)] backdrop-blur-md transition hover:-translate-y-0.5 hover:text-fg-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary-5"
      >
        <X className="size-5" aria-hidden />
      </button>

      <div
        ref={printerDeviceRef}
        className="pointer-events-none absolute bottom-[-5.25rem] left-1/2 z-30 w-[clamp(19rem,48vw,36rem)] -translate-x-1/2"
        aria-hidden
      >
        <motion.div
          initial={{ y: DEVICE_VERTICAL_OFFSET, opacity: 0 }}
          animate={{
            y: [DEVICE_VERTICAL_OFFSET, 0, 0, DEVICE_VERTICAL_OFFSET + 20],
            opacity: [0, 1, 1, 0],
          }}
          transition={{
            duration: DEVICE_REVEAL_DURATION_SECONDS,
            times: DEVICE_REVEAL_TIMES,
            ease: DEVICE_REVEAL_EASE,
          }}
        >
          <Image
            src={NEMONIC_DEVICE_IMAGE_SRC}
            alt=""
            width={NEMONIC_DEVICE_IMAGE_WIDTH}
            height={NEMONIC_DEVICE_IMAGE_HEIGHT}
            priority
            unoptimized
            className="h-auto w-full max-w-none object-contain drop-shadow-[0_22px_42px_rgb(34_26_52_/_26%)]"
          />
        </motion.div>
      </div>

      {printerSlotMetrics && viewportMetrics && (
        <>
          {printPhase === 'printing' && (
            <PrintingMemoFromSlot
              placement={placement}
              printerSlotMetrics={printerSlotMetrics}
              slotMemoSize={slotMemoSize}
              onPrintComplete={scheduleExpandPhase}
            />
          )}

          {printPhase === 'expanding' && (
            <PrintedMemoPlacementButton
              placement={placement}
              printerSlotMetrics={printerSlotMetrics}
              viewportMetrics={viewportMetrics}
              slotMemoSize={slotMemoSize}
              centerMemoSize={centerMemoSize}
              isPrintedMemoReady={isPrintedMemoReady}
              onReady={() => setPrintedMemoReady(true)}
              onAccept={onAccept}
            />
          )}

          {isPrintedMemoReady && (
            <PrintedMemoPickupHint
              viewportMetrics={viewportMetrics}
              centerMemoSize={centerMemoSize}
            />
          )}

          <PrinterOutputSlot printerSlotMetrics={printerSlotMetrics} />
        </>
      )}
    </div>
  )
}

function PrintedMemoPickupHint({
  viewportMetrics,
  centerMemoSize,
}: {
  viewportMetrics: ViewportMetrics
  centerMemoSize: number
}) {
  const memoBottom = viewportMetrics.centerY + centerMemoSize / 2
  const hintCenterY = memoBottom + (viewportMetrics.height - memoBottom) / 2

  return (
    <motion.p
      className="body-l-b pointer-events-none absolute left-1/2 z-[65] w-[min(calc(100vw-2rem),26rem)] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#19172a]/70 px-6 py-3.5 text-center text-white shadow-[0_14px_30px_rgb(25_20_40_/_24%)] backdrop-blur-md"
      style={{ top: hintCenterY }}
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.24, ease: [0.16, 0.78, 0.18, 1] }}
    >
      메모지를 클릭해 집어주세요!
    </motion.p>
  )
}

function PrintingMemoFromSlot({
  placement,
  printerSlotMetrics,
  slotMemoSize,
  onPrintComplete,
}: {
  placement: CommunityPendingMemoPlacement
  printerSlotMetrics: PrinterSlotMetrics
  slotMemoSize: number
  onPrintComplete: () => void
}) {
  useEffect(() => {
    const printCompleteTimerId = window.setTimeout(
      () => {
        playBrowserAudio(
          MEMO_PRINT_COMPLETE_SOUND_PATH,
          MEMO_PRINT_COMPLETE_SOUND_VOLUME,
          MEMO_PRINT_COMPLETE_SOUND_OFFSET_SECONDS,
        )
      },
      Math.max(
        PAPER_PRINT_START_DELAY_SECONDS
          + PAPER_PRINT_DURATION_SECONDS
          - MEMO_PRINT_COMPLETE_SOUND_LEAD_SECONDS,
        0,
      ) * 1000,
    )

    return () => {
      window.clearTimeout(printCompleteTimerId)
    }
  }, [])

  return (
    <motion.div
      className="pointer-events-none absolute z-40 overflow-hidden"
      initial={{
        left: printerSlotMetrics.centerX - slotMemoSize / 2,
        top: printerSlotMetrics.outputBaselineY,
        width: slotMemoSize,
        height: 0,
        opacity: 1,
        rotate: 0,
      }}
      animate={{
        top: printerSlotMetrics.outputBaselineY - slotMemoSize,
        height: slotMemoSize,
        opacity: 1,
        rotate: 0,
      }}
      transition={{
        delay: PAPER_PRINT_START_DELAY_SECONDS,
        duration: PAPER_PRINT_DURATION_SECONDS,
        ease: PAPER_EASE,
      }}
      onAnimationComplete={onPrintComplete}
    >
      <div
        className="absolute left-0 top-0"
        style={{
          height: slotMemoSize,
          width: slotMemoSize,
        }}
      >
        <PrintedMemoSurface
          placement={placement}
          isPrintedMemoReady={false}
        />
      </div>
    </motion.div>
  )
}

function PrintedMemoPlacementButton({
  placement,
  printerSlotMetrics,
  viewportMetrics,
  slotMemoSize,
  centerMemoSize,
  isPrintedMemoReady,
  onReady,
  onAccept,
}: {
  placement: CommunityPendingMemoPlacement
  printerSlotMetrics: PrinterSlotMetrics
  viewportMetrics: ViewportMetrics
  slotMemoSize: number
  centerMemoSize: number
  isPrintedMemoReady: boolean
  onReady: () => void
  onAccept: () => void
}) {
  return (
    <motion.button
      type="button"
      aria-label="Pick up printed memo"
      disabled={!isPrintedMemoReady}
      onClick={onAccept}
      className={cn(
        'absolute z-40 grid place-items-center overflow-visible rounded-[0.65rem] focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary-5',
        isPrintedMemoReady ? 'cursor-pointer' : 'cursor-default',
      )}
      initial={{
        left: printerSlotMetrics.centerX - slotMemoSize / 2,
        top: printerSlotMetrics.outputBaselineY - slotMemoSize,
        width: slotMemoSize,
        height: slotMemoSize,
        opacity: 1,
        rotate: 0,
      }}
      animate={{
        left: viewportMetrics.centerX - centerMemoSize / 2,
        top: viewportMetrics.centerY - centerMemoSize / 2,
        width: centerMemoSize,
        height: centerMemoSize,
        opacity: 1,
        rotate: 0,
      }}
      transition={{
        duration: PAPER_EXPAND_DURATION_SECONDS,
        ease: [0.14, 0.84, 0.18, 1],
      }}
      onAnimationComplete={onReady}
    >
      <PrintedMemoSurface
        placement={placement}
        isPrintedMemoReady={isPrintedMemoReady}
      />
    </motion.button>
  )
}

function PrinterOutputSlot({
  printerSlotMetrics,
}: {
  printerSlotMetrics: PrinterSlotMetrics
}) {
  return (
    <motion.div
      className="pointer-events-none absolute z-50 rotate-[1.75deg] overflow-visible"
      initial={{
        left: printerSlotMetrics.left,
        top: printerSlotMetrics.top,
        width: printerSlotMetrics.width,
        height: printerSlotMetrics.height,
        y: DEVICE_VERTICAL_OFFSET,
        opacity: 0,
      }}
      animate={{
        y: [DEVICE_VERTICAL_OFFSET, 0, 0, DEVICE_VERTICAL_OFFSET + 20],
        opacity: [0, 1, 1, 0],
      }}
      transition={{
        duration: DEVICE_REVEAL_DURATION_SECONDS,
        times: DEVICE_REVEAL_TIMES,
        ease: DEVICE_REVEAL_EASE,
      }}
      aria-hidden
    >
      <Image
        src={NEMONIC_OUTPUT_SLOT_IMAGE_SRC}
        alt=""
        fill
        sizes="(max-width: 768px) 38vw, 248px"
        unoptimized
        className="size-full max-w-none object-fill drop-shadow-[0_2px_2px_rgba(68,29,0,0.16)]"
      />
    </motion.div>
  )
}

function PrintedMemoSurface({
  placement,
  isPrintedMemoReady,
}: {
  placement: CommunityPendingMemoPlacement
  isPrintedMemoReady: boolean
}) {
  return (
    <span
      className="relative block size-full drop-shadow-[0_18px_30px_rgb(34_26_52_/_24%)]"
      style={{ color: placement.memoColor }}
    >
      <PostItNote
        shape="square"
        motion={isPrintedMemoReady ? 'active' : 'none'}
        selected={isPrintedMemoReady}
        className="absolute inset-0 size-full"
      />
      <span
        data-post-it-art-motion={isPrintedMemoReady ? 'active' : undefined}
        className="post-it-note-art absolute inset-x-[10%] bottom-[13%] top-[15%] overflow-hidden rounded-[0.35rem]"
      >
        <Image
          src={placement.previewUrl}
          alt=""
          fill
          sizes="(max-width: 768px) 52vw, 220px"
          unoptimized
          draggable={false}
          className="object-contain"
        />
      </span>
    </span>
  )
}

function getCenterMemoSize(viewportWidth: number) {
  return Math.min(Math.max(viewportWidth * 0.32, 160), 220)
}

function getSlotMemoSize(slotWidth: number, centerMemoSize: number) {
  return Math.min(Math.max(slotWidth * 0.9, 112), centerMemoSize * 0.9)
}
