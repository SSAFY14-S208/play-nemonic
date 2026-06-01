import type { ReactNode } from 'react'
import Image from 'next/image'

import { cn } from '@/shared/libs'

import {
  BOARD_IMAGE_HEIGHT,
  BOARD_IMAGE_SRC,
  BOARD_IMAGE_WIDTH,
  FURNITURE_IMAGE_HEIGHT,
  FURNITURE_IMAGE_SRC,
  FURNITURE_IMAGE_WIDTH,
  NEMONIC_DEVICE_IMAGE_HEIGHT,
  NEMONIC_DEVICE_IMAGE_SRC,
  NEMONIC_DEVICE_IMAGE_WIDTH,
  NEMONIC_OUTPUT_SLOT_IMAGE_SRC,
  RESULT_STAGE_BACKGROUND_IMAGE_HEIGHT,
  RESULT_STAGE_BACKGROUND_IMAGE_SRC,
  RESULT_STAGE_BACKGROUND_IMAGE_WIDTH,
  resultPrintStageStyles as styles,
} from './constants'

export function StageBackground() {
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

export function FurnitureLayers() {
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

export function BoardLayer({ children }: { children: ReactNode }) {
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

export function NemonicDeviceImage({
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
