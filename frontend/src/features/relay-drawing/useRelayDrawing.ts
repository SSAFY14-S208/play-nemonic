'use client'

import { useCallback, useMemo, useState } from 'react'
import type { KonvaEventObject } from 'konva/lib/Node'
import {
  RELAY_COLORS,
  RELAY_RESULT_REVEALS,
  RELAY_ROUND_ORDER,
  RELAY_ROUND_RULES,
  RELAY_STAGE_SIZE,
  RELAY_STEPS,
  type RelayDrawingStep,
  type RelayRoundArea,
  type RelayResultRevealStep,
  type RelayRoundKey,
  type RelayToolKey,
} from './constants'

export interface RelayDrawPoint {
  x: number
  y: number
}

export interface RelayDrawLine {
  id: string
  color: string
  strokeWidth: number
  points: RelayDrawPoint[]
}

export type RelayRoundLines = Record<RelayRoundKey, RelayDrawLine[]>

export interface RelayCompositeDrawingPayload {
  rounds: RelayRoundLines
  mergedLines: RelayDrawLine[]
  completedAt: string | null
}

const DEFAULT_STROKE_WIDTH = 4
const DEFAULT_ROUND_LINES: RelayRoundLines = {
  face: [],
  body: [],
  legs: [],
}

function isPointInsideArea(point: RelayDrawPoint, area: RelayRoundArea) {
  return (
    point.x >= 0 &&
    point.x <= RELAY_STAGE_SIZE.width &&
    point.y >= area.y &&
    point.y <= area.y + area.height
  )
}

function moveLineToFinalPosition(line: RelayDrawLine, roundKey: RelayRoundKey): RelayDrawLine {
  const roundRule = RELAY_ROUND_RULES[roundKey]

  return {
    ...line,
    id: `${roundKey}-${line.id}`,
    points: line.points.map((point) => ({
      x: point.x,
      y: point.y + roundRule.finalOffsetY,
    })),
  }
}

export function useRelayDrawing() {
  const [currentStep, setCurrentStep] = useState<RelayDrawingStep>('booth')
  const [activeRoundKey, setActiveRoundKey] = useState<RelayRoundKey>('face')
  const [selectedToolKey, setSelectedToolKey] = useState<RelayToolKey>('pencil')
  const [selectedColor, setSelectedColor] = useState(RELAY_COLORS[1])
  const [strokeWidth, setStrokeWidth] = useState(DEFAULT_STROKE_WIDTH)
  const [roundLines, setRoundLines] = useState<RelayRoundLines>(DEFAULT_ROUND_LINES)
  const [isDrawing, setIsDrawing] = useState(false)
  const [resultRevealStep, setResultRevealStep] = useState<RelayResultRevealStep>('final')
  const [completedAt, setCompletedAt] = useState<string | null>(null)

  const currentStepIndex = RELAY_STEPS.findIndex((step) => step.key === currentStep)
  const activeRoundIndex = RELAY_ROUND_ORDER.findIndex((roundKey) => roundKey === activeRoundKey)
  const currentResultRevealIndex = RELAY_RESULT_REVEALS.findIndex(
    (step) => step.key === resultRevealStep,
  )
  const canGoBack = currentStepIndex > 0
  const canAdvance = currentStepIndex < RELAY_STEPS.length - 1
  const canShowPreviousResultReveal = currentResultRevealIndex > 0
  const canShowNextResultReveal = currentResultRevealIndex < RELAY_RESULT_REVEALS.length - 1

  const activeRoundLines = roundLines[activeRoundKey]
  const previousRoundKey = activeRoundIndex > 0 ? RELAY_ROUND_ORDER[activeRoundIndex - 1] : null
  const previousRoundLines = previousRoundKey ? roundLines[previousRoundKey] : []
  const stageColor = selectedToolKey === 'eraser' ? '#fffdf7' : selectedColor
  const activeStrokeWidth = selectedToolKey === 'marker' ? strokeWidth + 4 : strokeWidth

  const resetDrawingSession = useCallback(() => {
    setActiveRoundKey('face')
    setRoundLines({
      face: [],
      body: [],
      legs: [],
    })
    setCompletedAt(null)
    setResultRevealStep('final')
  }, [])

  const updateActiveRoundLines = useCallback(
    (updater: (currentLines: RelayDrawLine[]) => RelayDrawLine[]) => {
      setRoundLines((currentRoundLines) => ({
        ...currentRoundLines,
        [activeRoundKey]: updater(currentRoundLines[activeRoundKey]),
      }))
    },
    [activeRoundKey],
  )

  const selectStep = useCallback(
    (step: RelayDrawingStep) => {
      setCurrentStep(step)
      if (step === 'drawing') {
        setActiveRoundKey('face')
      }
      if (step === 'result') {
        setResultRevealStep('final')
      }
    },
    [],
  )

  const goToNextStep = useCallback(() => {
    const nextStep = RELAY_STEPS[Math.min(currentStepIndex + 1, RELAY_STEPS.length - 1)]
    setCurrentStep(nextStep.key)
    if (nextStep.key === 'drawing') {
      resetDrawingSession()
    }
    if (nextStep.key === 'result') {
      setResultRevealStep('final')
    }
  }, [currentStepIndex, resetDrawingSession])

  const goToPreviousStep = useCallback(() => {
    const previousStep = RELAY_STEPS[Math.max(currentStepIndex - 1, 0)]
    setCurrentStep(previousStep.key)
  }, [currentStepIndex])

  const completeRound = useCallback(() => {
    const nextRoundKey = RELAY_ROUND_ORDER[activeRoundIndex + 1]

    if (nextRoundKey) {
      setActiveRoundKey(nextRoundKey)
      setIsDrawing(false)
      return
    }

    setCompletedAt(new Date().toISOString())
    setResultRevealStep('final')
    setCurrentStep('result')
    setIsDrawing(false)
  }, [activeRoundIndex])

  const goToNextResultReveal = useCallback(() => {
    const nextReveal = RELAY_RESULT_REVEALS[
      Math.min(currentResultRevealIndex + 1, RELAY_RESULT_REVEALS.length - 1)
    ]
    setResultRevealStep(nextReveal.key)
  }, [currentResultRevealIndex])

  const goToPreviousResultReveal = useCallback(() => {
    const previousReveal = RELAY_RESULT_REVEALS[Math.max(currentResultRevealIndex - 1, 0)]
    setResultRevealStep(previousReveal.key)
  }, [currentResultRevealIndex])

  const clearDrawing = useCallback(() => {
    updateActiveRoundLines(() => [])
  }, [updateActiveRoundLines])

  const undoDrawing = useCallback(() => {
    updateActiveRoundLines((currentLines) => currentLines.slice(0, -1))
  }, [updateActiveRoundLines])

  const beginDrawing = useCallback(
    (event: KonvaEventObject<MouseEvent | TouchEvent>) => {
      const stage = event.target.getStage()
      const pointerPosition = stage?.getPointerPosition()
      if (!pointerPosition) return
      if (!isPointInsideArea(pointerPosition, RELAY_ROUND_RULES[activeRoundKey].drawArea)) return

      setIsDrawing(true)
      updateActiveRoundLines((currentLines) => [
        ...currentLines,
        {
          id: `${activeRoundKey}-line-${Date.now()}-${currentLines.length}`,
          color: stageColor,
          strokeWidth: activeStrokeWidth,
          points: [{ x: pointerPosition.x, y: pointerPosition.y }],
        },
      ])
    },
    [activeRoundKey, activeStrokeWidth, stageColor, updateActiveRoundLines],
  )

  const continueDrawing = useCallback(
    (event: KonvaEventObject<MouseEvent | TouchEvent>) => {
      if (!isDrawing) return

      const stage = event.target.getStage()
      const pointerPosition = stage?.getPointerPosition()
      if (!pointerPosition) return

      if (!isPointInsideArea(pointerPosition, RELAY_ROUND_RULES[activeRoundKey].drawArea)) {
        setIsDrawing(false)
        return
      }

      updateActiveRoundLines((currentLines) => {
        const latestLine = currentLines[currentLines.length - 1]
        if (!latestLine) return currentLines

        const updatedLine: RelayDrawLine = {
          ...latestLine,
          points: [...latestLine.points, { x: pointerPosition.x, y: pointerPosition.y }],
        }

        return [...currentLines.slice(0, -1), updatedLine]
      })
    },
    [activeRoundKey, isDrawing, updateActiveRoundLines],
  )

  const endDrawing = useCallback(() => {
    setIsDrawing(false)
  }, [])

  const selectedStepLabel = useMemo(
    () => RELAY_STEPS.find((step) => step.key === currentStep)?.label ?? '',
    [currentStep],
  )

  const compositeDrawingPayload = useMemo<RelayCompositeDrawingPayload>(
    () => ({
      rounds: roundLines,
      mergedLines: RELAY_ROUND_ORDER.flatMap((roundKey) =>
        roundLines[roundKey].map((line) => moveLineToFinalPosition(line, roundKey)),
      ),
      completedAt,
    }),
    [completedAt, roundLines],
  )

  return {
    currentStep,
    selectedStepLabel,
    activeRoundKey,
    activeRoundIndex,
    selectedToolKey,
    resultRevealStep,
    selectedColor,
    strokeWidth,
    lines: activeRoundLines,
    previousRoundLines,
    roundLines,
    compositeDrawingPayload,
    canGoBack,
    canAdvance,
    canShowPreviousResultReveal,
    canShowNextResultReveal,
    selectStep,
    goToNextStep,
    goToPreviousStep,
    completeRound,
    goToNextResultReveal,
    goToPreviousResultReveal,
    setSelectedToolKey,
    setSelectedColor,
    setStrokeWidth,
    clearDrawing,
    undoDrawing,
    beginDrawing,
    continueDrawing,
    endDrawing,
  }
}
