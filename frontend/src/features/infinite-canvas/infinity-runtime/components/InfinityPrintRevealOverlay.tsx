'use client'

import { useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import { motion } from 'motion/react'
import { playBrowserAudio, preloadBrowserAudio } from '@/shared/utils'

interface InfinityPrintRevealOverlayProps {
  previewUrl: string | null
  isSaving: boolean
  onDone: () => void
}

const NEMONIC_DEVICE_IMAGE_SRC = '/images/flipbook-result/attached-nemonic-device-v3-hq.png'
const NEMONIC_OUTPUT_SLOT_IMAGE_SRC = '/images/flipbook-result/figma-node-2826-output-slot.svg'
const NEMONIC_DEVICE_IMAGE_WIDTH = 1551
const NEMONIC_DEVICE_IMAGE_HEIGHT = 1035
const NEMONIC_OUTPUT_SLOT_IMAGE_WIDTH = 224.578
const NEMONIC_OUTPUT_SLOT_IMAGE_HEIGHT = 6.41341
const DEVICE_REVEAL_DURATION_SECONDS = 3.05
const DEVICE_VERTICAL_OFFSET = 190
const DEVICE_HOLD_REVEAL_TIMES = [0, 0.14, 0.9, 1]
const DEVICE_REVEAL_EASE = [0.16, 0.78, 0.18, 1] as const
const PAPER_PRINT_START_DELAY_SECONDS = DEVICE_REVEAL_DURATION_SECONDS * 0.14
const PAPER_PRINT_DURATION_SECONDS = 1.36
const PAPER_EXPAND_DURATION_SECONDS = 0.84
const PAPER_EXPAND_DELAY_MS = 140
const PAPER_EASE = [0.12, 0.78, 0.16, 1] as const
const PRINT_START_SOUND_PATH = '/sounds/print_label.mp3'
const PRINT_COMPLETE_SOUND_PATH = '/sounds/cut_label.mp3'
const PRINT_START_SOUND_VOLUME = 0.36
const PRINT_COMPLETE_SOUND_VOLUME = 0.42
const PRINT_COMPLETE_SOUND_LEAD_SECONDS = 0.12
const PRINT_COMPLETE_SOUND_OFFSET_SECONDS = 0.08
const PRINTER_SLOT_LEFT_RATIO = 0.262
const PRINTER_SLOT_TOP_RATIO = 0.192
const PRINTER_SLOT_WIDTH_RATIO = 0.429
const OUTPUT_SLOT_HEIGHT_BY_WIDTH =
  NEMONIC_OUTPUT_SLOT_IMAGE_HEIGHT / NEMONIC_OUTPUT_SLOT_IMAGE_WIDTH

type PrinterSlotMetrics = {
  left: number
  top: number
  width: number
  centerX: number
  outputBaselineY: number
}

type ViewportMetrics = {
  centerX: number
  centerY: number
  width: number
}

type PrintPhase = 'printing' | 'expanding'

export function InfinityPrintRevealOverlay({
  previewUrl,
  isSaving,
  onDone,
}: InfinityPrintRevealOverlayProps) {
  if (!previewUrl) return null

  return (
    <InfinityPrintRevealScene
      key={previewUrl}
      previewUrl={previewUrl}
      isSaving={isSaving}
      onDone={onDone}
    />
  )
}

function InfinityPrintRevealScene({
  previewUrl,
  isSaving,
  onDone,
}: {
  previewUrl: string
  isSaving: boolean
  onDone: () => void
}) {
  const printerDeviceRef = useRef<HTMLDivElement>(null)
  const expandTimerRef = useRef<number | null>(null)
  const doneTimerRef = useRef<number | null>(null)
  const [printPhase, setPrintPhase] = useState<PrintPhase>('printing')
  const [printerSlotMetrics, setPrinterSlotMetrics] = useState<PrinterSlotMetrics | null>(null)
  const [viewportMetrics, setViewportMetrics] = useState<ViewportMetrics | null>(null)

  useEffect(() => {
    preloadBrowserAudio(PRINT_START_SOUND_PATH, PRINT_START_SOUND_VOLUME)
    preloadBrowserAudio(PRINT_COMPLETE_SOUND_PATH, PRINT_COMPLETE_SOUND_VOLUME)
    playBrowserAudio(PRINT_START_SOUND_PATH, PRINT_START_SOUND_VOLUME, 0.2)
  }, [])

  useEffect(() => {
    return () => {
      if (expandTimerRef.current !== null) window.clearTimeout(expandTimerRef.current)
      if (doneTimerRef.current !== null) window.clearTimeout(doneTimerRef.current)
    }
  }, [])

  useEffect(() => {
    let animationFrameId: number | null = null

    const measurePrinterSlot = () => {
      const printerDeviceRect = printerDeviceRef.current?.getBoundingClientRect()
      if (!printerDeviceRect) return

      const slotLeft = printerDeviceRect.left + printerDeviceRect.width * PRINTER_SLOT_LEFT_RATIO
      const slotTop = printerDeviceRect.top + printerDeviceRect.height * PRINTER_SLOT_TOP_RATIO
      const slotWidth = printerDeviceRect.width * PRINTER_SLOT_WIDTH_RATIO

      setPrinterSlotMetrics({
        left: slotLeft,
        top: slotTop,
        width: slotWidth,
        centerX: slotLeft + slotWidth / 2,
        outputBaselineY: slotTop,
      })
      setViewportMetrics({
        centerX: window.innerWidth / 2,
        centerY: window.innerHeight / 2,
        width: window.innerWidth,
      })
    }

    const schedulePrinterSlotMeasure = () => {
      if (animationFrameId !== null) window.cancelAnimationFrame(animationFrameId)
      animationFrameId = window.requestAnimationFrame(measurePrinterSlot)
    }

    schedulePrinterSlotMeasure()
    window.addEventListener('resize', schedulePrinterSlotMeasure)
    window.visualViewport?.addEventListener('resize', schedulePrinterSlotMeasure)

    return () => {
      if (animationFrameId !== null) window.cancelAnimationFrame(animationFrameId)
      window.removeEventListener('resize', schedulePrinterSlotMeasure)
      window.visualViewport?.removeEventListener('resize', schedulePrinterSlotMeasure)
    }
  }, [])

  const centerPrintSize = viewportMetrics ? getCenterPrintSize(viewportMetrics.width) : 0
  const slotPrintSize =
    printerSlotMetrics && viewportMetrics
      ? getSlotPrintSize(printerSlotMetrics.width, centerPrintSize)
      : 0

  const scheduleExpandPhase = () => {
    if (expandTimerRef.current !== null) window.clearTimeout(expandTimerRef.current)
    expandTimerRef.current = window.setTimeout(() => {
      setPrintPhase('expanding')
    }, PAPER_EXPAND_DELAY_MS)
  }

  const scheduleDone = () => {
    if (doneTimerRef.current !== null) window.clearTimeout(doneTimerRef.current)
    doneTimerRef.current = window.setTimeout(() => {
      if (!isSaving) onDone()
    }, 650)
  }

  useEffect(() => {
    if (!isSaving && printPhase === 'expanding') {
      scheduleDone()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isSaving, printPhase])

  return (
    <div className="fixed inset-0 z-[13000] overflow-hidden">
      <motion.div
        className="absolute inset-0 bg-[#1b2550]/32 backdrop-blur-[2px]"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.22 }}
        aria-hidden
      />

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
            times: DEVICE_HOLD_REVEAL_TIMES,
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
          <span
            className="absolute rotate-[1.75deg]"
            style={{
              left: `${PRINTER_SLOT_LEFT_RATIO * 100}%`,
              top: `${PRINTER_SLOT_TOP_RATIO * 100}%`,
              width: `${PRINTER_SLOT_WIDTH_RATIO * 100}%`,
              height: `${PRINTER_SLOT_WIDTH_RATIO * OUTPUT_SLOT_HEIGHT_BY_WIDTH * 100}%`,
            }}
          >
            <Image
              src={NEMONIC_OUTPUT_SLOT_IMAGE_SRC}
              alt=""
              fill
              sizes="(max-width: 768px) 38vw, 248px"
              unoptimized
              className="size-full max-w-none object-fill drop-shadow-[0_2px_2px_rgba(68,29,0,0.16)]"
            />
          </span>
        </motion.div>
      </div>

      {printerSlotMetrics && viewportMetrics && (
        <>
          {printPhase === 'printing' && (
            <PrintingImageFromSlot
              previewUrl={previewUrl}
              printerSlotMetrics={printerSlotMetrics}
              slotPrintSize={slotPrintSize}
              onPrintComplete={scheduleExpandPhase}
            />
          )}

          {printPhase === 'expanding' && (
            <PrintedImagePreview
              previewUrl={previewUrl}
              printerSlotMetrics={printerSlotMetrics}
              viewportMetrics={viewportMetrics}
              slotPrintSize={slotPrintSize}
              centerPrintSize={centerPrintSize}
              onReady={scheduleDone}
            />
          )}
        </>
      )}
    </div>
  )
}

function PrintingImageFromSlot({
  previewUrl,
  printerSlotMetrics,
  slotPrintSize,
  onPrintComplete,
}: {
  previewUrl: string
  printerSlotMetrics: PrinterSlotMetrics
  slotPrintSize: number
  onPrintComplete: () => void
}) {
  useEffect(() => {
    const printCompleteTimerId = window.setTimeout(
      () => {
        playBrowserAudio(
          PRINT_COMPLETE_SOUND_PATH,
          PRINT_COMPLETE_SOUND_VOLUME,
          PRINT_COMPLETE_SOUND_OFFSET_SECONDS,
        )
      },
      Math.max(
        PAPER_PRINT_START_DELAY_SECONDS
          + PAPER_PRINT_DURATION_SECONDS
          - PRINT_COMPLETE_SOUND_LEAD_SECONDS,
        0,
      ) * 1000,
    )

    return () => window.clearTimeout(printCompleteTimerId)
  }, [])

  return (
    <motion.div
      className="pointer-events-none absolute z-40 overflow-hidden"
      initial={{
        left: printerSlotMetrics.centerX - slotPrintSize / 2,
        top: printerSlotMetrics.outputBaselineY,
        width: slotPrintSize,
        height: 0,
        opacity: 1,
      }}
      animate={{
        top: printerSlotMetrics.outputBaselineY - slotPrintSize,
        height: slotPrintSize,
        opacity: 1,
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
        style={{ height: slotPrintSize, width: slotPrintSize }}
      >
        <PrintedImageSurface previewUrl={previewUrl} />
      </div>
    </motion.div>
  )
}

function PrintedImagePreview({
  previewUrl,
  printerSlotMetrics,
  viewportMetrics,
  slotPrintSize,
  centerPrintSize,
  onReady,
}: {
  previewUrl: string
  printerSlotMetrics: PrinterSlotMetrics
  viewportMetrics: ViewportMetrics
  slotPrintSize: number
  centerPrintSize: number
  onReady: () => void
}) {
  return (
    <motion.div
      className="pointer-events-none absolute z-40 grid place-items-center overflow-visible rounded-[0.75rem]"
      initial={{
        left: printerSlotMetrics.centerX - slotPrintSize / 2,
        top: printerSlotMetrics.outputBaselineY - slotPrintSize,
        width: slotPrintSize,
        height: slotPrintSize,
        opacity: 1,
      }}
      animate={{
        left: viewportMetrics.centerX - centerPrintSize / 2,
        top: viewportMetrics.centerY - centerPrintSize / 2,
        width: centerPrintSize,
        height: centerPrintSize,
        opacity: 1,
      }}
      transition={{
        duration: PAPER_EXPAND_DURATION_SECONDS,
        ease: [0.14, 0.84, 0.18, 1],
      }}
      onAnimationComplete={onReady}
    >
      <PrintedImageSurface previewUrl={previewUrl} />
    </motion.div>
  )
}

function PrintedImageSurface({ previewUrl }: { previewUrl: string }) {
  return (
    <span className="relative block size-full overflow-visible rounded-[0.85rem] bg-white shadow-[0_18px_30px_rgb(34_26_52_/_24%)]">
      <span className="absolute inset-[2px] overflow-hidden rounded-[0.72rem]">
        <Image
          src={previewUrl}
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

function getCenterPrintSize(viewportWidth: number) {
  return Math.min(Math.max(viewportWidth * 0.32, 170), 240)
}

function getSlotPrintSize(slotWidth: number, centerPrintSize: number) {
  return Math.min(Math.max(slotWidth * 0.9, 112), centerPrintSize * 0.9)
}
