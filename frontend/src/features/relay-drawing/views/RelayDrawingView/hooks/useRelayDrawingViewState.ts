'use client'

import { RELAY_ROUND_ORDER, RELAY_ROUND_SEGMENTS } from '@/features/relay-drawing/constants'
import { useRelayDrawingStore } from '@/features/relay-drawing/stores'
import { useDrawingKeyboardShortcuts } from '@/shared/hooks'
import type { DrawingToolKey } from '@/shared/types'

import { useRelayDrawingGame } from './useRelayDrawingGame'
import { useRelayTimer } from './useRelayTimer'

export function useRelayDrawingViewState() {
  const activeRoundKey = useRelayDrawingStore((state) => state.activeRoundKey)
  const isPartTimeUp = useRelayDrawingStore((state) => state.isPartTimeUp)
  const selectedToolKey = useRelayDrawingStore((state) => state.selectedToolKey)
  const selectedColor = useRelayDrawingStore((state) => state.selectedColor)
  const selectedOpacity = useRelayDrawingStore((state) => state.selectedOpacity)
  const strokeWidth = useRelayDrawingStore((state) => state.strokeWidth)
  const recentColors = useRelayDrawingStore((state) => state.recentColors)
  const roundLines = useRelayDrawingStore((state) => state.roundLines)
  const roundRedoStack = useRelayDrawingStore((state) => state.roundRedoStack)
  const setSelectedToolKey = useRelayDrawingStore((state) => state.setSelectedToolKey)
  const setSelectedColor = useRelayDrawingStore((state) => state.setSelectedColor)
  const setSelectedOpacity = useRelayDrawingStore((state) => state.setSelectedOpacity)
  const setStrokeWidth = useRelayDrawingStore((state) => state.setStrokeWidth)
  const undoLine = useRelayDrawingStore((state) => state.undoLine)
  const redoLine = useRelayDrawingStore((state) => state.redoLine)
  const clearRoundLines = useRelayDrawingStore((state) => state.clearRoundLines)

  const { remainingSeconds, formattedTime } = useRelayTimer()
  const { submitDrawing, isSubmitting, isSubmitted, submittedCount, totalCount } =
    useRelayDrawingGame()

  const activeRound = RELAY_ROUND_SEGMENTS[activeRoundKey]
  const activeRoundIndex = RELAY_ROUND_ORDER.findIndex(
    (roundKey) => roundKey === activeRoundKey,
  )
  const isLastRound = activeRoundKey === 'legs'
  const canUndoDrawing = roundLines[activeRoundKey].length > 0
  const canRedoDrawing = roundRedoStack[activeRoundKey].length > 0
  const isDrawingLocked = isSubmitting || isSubmitted || isPartTimeUp
  const completionStatusText =
    isSubmitted && totalCount > 0 ? ` (${submittedCount}/${totalCount})` : ''
  const buttonLabel = (() => {
    if (isSubmitting) return '제출 중'
    if (isSubmitted) return `대기 중${completionStatusText}`
    return isLastRound ? '완료하기' : `${activeRound.label} 저장하기`
  })()
  const overlayMessage = isPartTimeUp
    ? '다음 파트를 준비하고 있어요'
    : isSubmitted
      ? '제출 완료! 다음 파트를 기다리는 중이에요'
      : isSubmitting
        ? '그림을 제출하고 있어요'
        : null

  const handleSelectTool = (toolKey: DrawingToolKey) => {
    if (toolKey === 'marker') return
    setSelectedToolKey(toolKey)
  }

  const handleSubmitDrawing = () => {
    void submitDrawing()
  }

  useDrawingKeyboardShortcuts({
    enabled: !isDrawingLocked,
    onUndo: undoLine,
    onRedo: redoLine,
  })

  return {
    // Drawing tool state
    selectedToolKey,
    selectedColor,
    selectedOpacity,
    strokeWidth,
    recentColors,
    setSelectedColor,
    setSelectedOpacity,
    setStrokeWidth,
    undoLine,
    redoLine,
    clearRoundLines,
    // Drawing capability
    canUndoDrawing,
    canRedoDrawing,
    isDrawingLocked,
    // Round info
    isPartTimeUp,
    activeRound,
    activeRoundIndex,
    isLastRound,
    // Timer
    remainingSeconds,
    formattedTime,
    // Submit action
    handleSelectTool,
    handleSubmitDrawing,
    // UI text
    buttonLabel,
    overlayMessage,
  }
}
