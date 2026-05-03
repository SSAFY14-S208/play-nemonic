'use client'

import { useCallback, useMemo, useState } from 'react'
import type { KonvaEventObject } from 'konva/lib/Node'
import {
  RELAY_COLORS,
  RELAY_RESULT_REVEALS,
  RELAY_STEPS,
  type RelayDrawingStep,
  type RelayResultRevealStep,
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

const DEFAULT_STROKE_WIDTH = 4

export function useRelayDrawing() {
  const [currentStep, setCurrentStep] = useState<RelayDrawingStep>('booth')
  const [selectedToolKey, setSelectedToolKey] = useState<RelayToolKey>('pencil')
  const [selectedColor, setSelectedColor] = useState(RELAY_COLORS[1])
  const [strokeWidth, setStrokeWidth] = useState(DEFAULT_STROKE_WIDTH)
  const [lines, setLines] = useState<RelayDrawLine[]>([])
  const [isDrawing, setIsDrawing] = useState(false)
  const [resultRevealStep, setResultRevealStep] = useState<RelayResultRevealStep>('face')

  const currentStepIndex = RELAY_STEPS.findIndex((step) => step.key === currentStep)
  const canGoBack = currentStepIndex > 0
  const canAdvance = currentStepIndex < RELAY_STEPS.length - 1
  const currentResultRevealIndex = RELAY_RESULT_REVEALS.findIndex(
    (step) => step.key === resultRevealStep,
  )
  const canShowPreviousResultReveal = currentResultRevealIndex > 0
  const canShowNextResultReveal = currentResultRevealIndex < RELAY_RESULT_REVEALS.length - 1

  const stageColor = selectedToolKey === 'eraser' ? '#fffdf7' : selectedColor
  const activeStrokeWidth = selectedToolKey === 'marker' ? strokeWidth + 4 : strokeWidth

  const selectStep = useCallback((step: RelayDrawingStep) => {
    setCurrentStep(step)
    if (step === 'result') {
      setResultRevealStep('face')
    }
  }, [])

  const goToNextStep = useCallback(() => {
    const nextStep = RELAY_STEPS[Math.min(currentStepIndex + 1, RELAY_STEPS.length - 1)]
    setCurrentStep(nextStep.key)
    if (nextStep.key === 'result') {
      setResultRevealStep('face')
    }
  }, [currentStepIndex])

  const goToPreviousStep = useCallback(() => {
    const previousStep = RELAY_STEPS[Math.max(currentStepIndex - 1, 0)]
    setCurrentStep(previousStep.key)
  }, [currentStepIndex])

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
    setLines([])
  }, [])

  const undoDrawing = useCallback(() => {
    setLines((currentLines) => currentLines.slice(0, -1))
  }, [])

  const beginDrawing = useCallback(
    (event: KonvaEventObject<MouseEvent | TouchEvent>) => {
      const stage = event.target.getStage()
      const pointerPosition = stage?.getPointerPosition()
      if (!pointerPosition) return

      setIsDrawing(true)
      setLines((currentLines) => [
        ...currentLines,
        {
          id: `line-${Date.now()}-${currentLines.length}`,
          color: stageColor,
          strokeWidth: activeStrokeWidth,
          points: [{ x: pointerPosition.x, y: pointerPosition.y }],
        },
      ])
    },
    [activeStrokeWidth, stageColor],
  )

  const continueDrawing = useCallback(
    (event: KonvaEventObject<MouseEvent | TouchEvent>) => {
      if (!isDrawing) return

      const stage = event.target.getStage()
      const pointerPosition = stage?.getPointerPosition()
      if (!pointerPosition) return

      setLines((currentLines) => {
        const latestLine = currentLines[currentLines.length - 1]
        if (!latestLine) return currentLines

        const updatedLine: RelayDrawLine = {
          ...latestLine,
          points: [...latestLine.points, { x: pointerPosition.x, y: pointerPosition.y }],
        }

        return [...currentLines.slice(0, -1), updatedLine]
      })
    },
    [isDrawing],
  )

  const endDrawing = useCallback(() => {
    setIsDrawing(false)
  }, [])

  const selectedStepLabel = useMemo(
    () => RELAY_STEPS.find((step) => step.key === currentStep)?.label ?? '',
    [currentStep],
  )

  return {
    currentStep,
    selectedStepLabel,
    selectedToolKey,
    resultRevealStep,
    selectedColor,
    strokeWidth,
    lines,
    canGoBack,
    canAdvance,
    canShowPreviousResultReveal,
    canShowNextResultReveal,
    selectStep,
    goToNextStep,
    goToPreviousStep,
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
