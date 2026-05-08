'use client'

import { DrawingBoard } from '@/shared/components'
import type { DrawingLine, DrawingPointerEvent } from '@/shared/types'
import {
  FLIPBOOK_BACKGROUND_COLOR,
  FLIPBOOK_BOARD_SIZE,
} from './constants'

interface FlipbookStageProps {
  lines: DrawingLine[]
  previousFrameLines: DrawingLine[]
  onDrawStart: (event: DrawingPointerEvent) => void
  onDrawMove: (event: DrawingPointerEvent) => void
  onDrawEnd: () => void
}

export default function FlipbookStage({
  lines,
  previousFrameLines,
  onDrawStart,
  onDrawMove,
  onDrawEnd,
}: FlipbookStageProps) {
  return (
    <DrawingBoard
      boardSize={FLIPBOOK_BOARD_SIZE}
      lines={lines}
      onionSkinLines={previousFrameLines}
      backgroundColor={FLIPBOOK_BACKGROUND_COLOR}
      gridColor={FLIPBOOK_BACKGROUND_COLOR}
      gridGap={20}
      onionSkinOpacity={0.2}
      className="h-full w-full"
      onDrawStart={onDrawStart}
      onDrawMove={onDrawMove}
      onDrawEnd={onDrawEnd}
    />
  )
}
