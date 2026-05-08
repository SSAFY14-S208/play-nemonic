'use client'

import { DrawingBoard } from '@/shared/components'
import { cn } from '@/shared/libs'
import type { DrawingLine, DrawingPointerEvent } from '@/shared/types'
import {
  FLIPBOOK_BACKGROUND_COLOR,
  FLIPBOOK_BOARD_SIZE,
} from './constants'

interface FlipbookStageProps {
  lines: DrawingLine[]
  previousFrameLines: DrawingLine[]
  disabled?: boolean
  onDrawStart: (event: DrawingPointerEvent) => void
  onDrawMove: (event: DrawingPointerEvent) => void
  onDrawEnd: () => void
}

export default function FlipbookStage({
  lines,
  previousFrameLines,
  disabled = false,
  onDrawStart,
  onDrawMove,
  onDrawEnd,
}: FlipbookStageProps) {
  const handleDrawStart = disabled ? () => undefined : onDrawStart
  const handleDrawMove = disabled ? () => undefined : onDrawMove
  const handleDrawEnd = disabled ? () => undefined : onDrawEnd

  return (
    <DrawingBoard
      boardSize={FLIPBOOK_BOARD_SIZE}
      lines={lines}
      onionSkinLines={previousFrameLines}
      backgroundColor={FLIPBOOK_BACKGROUND_COLOR}
      gridColor={FLIPBOOK_BACKGROUND_COLOR}
      gridGap={20}
      onionSkinOpacity={0.16}
      className={cn('h-full w-full', disabled && 'pointer-events-none')}
      onDrawStart={handleDrawStart}
      onDrawMove={handleDrawMove}
      onDrawEnd={handleDrawEnd}
    />
  )
}
