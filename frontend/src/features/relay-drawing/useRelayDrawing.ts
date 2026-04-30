'use client'

import { useCallback, useMemo, useState } from 'react'
import type { KonvaEventObject } from 'konva/lib/Node'
import {
  RELAY_COLORS,
  RELAY_STEPS,
  type RelayDrawingStep,
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

  const currentStepIndex = RELAY_STEPS.findIndex((step) => step.key === currentStep)
  const canGoBack = currentStepIndex > 0
  const canAdvance = currentStepIndex < RELAY_STEPS.length - 1

  const stageColor = selectedToolKey === 'eraser' ? '#fffdf7' : selectedColor
  const activeStrokeWidth = selectedToolKey === 'marker' ? strokeWidth + 4 : strokeWidth

  const selectStep = useCallback((step: RelayDrawingStep) => {
    setCurrentStep(step)
  }, [])

  const goToNextStep = useCallback(() => {
    setCurrentStep((step) => {
      const stepIndex = RELAY_STEPS.findIndex((relayStep) => relayStep.key === step)
      const nextStep = RELAY_STEPS[Math.min(stepIndex + 1, RELAY_STEPS.length - 1)]
      return nextStep.key
    })
  }, [])

  const goToPreviousStep = useCallback(() => {
    setCurrentStep((step) => {
      const stepIndex = RELAY_STEPS.findIndex((relayStep) => relayStep.key === step)
      const previousStep = RELAY_STEPS[Math.max(stepIndex - 1, 0)]
      return previousStep.key
    })
  }, [])

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
    selectedColor,
    strokeWidth,
    lines,
    canGoBack,
    canAdvance,
    selectStep,
    goToNextStep,
    goToPreviousStep,
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
