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
  kind?: 'stroke' | 'fill'
  imageDataUrl?: string
}

export type RelayRoundLines = Record<RelayRoundKey, RelayDrawLine[]>

export interface RelayCompositeDrawingPayload {
  rounds: RelayRoundLines
  mergedLines: RelayDrawLine[]
  completedAt: string | null
}

const DEFAULT_STROKE_WIDTH = 4
const TRANSPARENT_ALPHA_TOLERANCE = 16
const COLOR_MATCH_TOLERANCE = 12
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

function parseHexColor(hexColor: string) {
  const normalizedHex = hexColor.replace('#', '')
  const red = Number.parseInt(normalizedHex.slice(0, 2), 16)
  const green = Number.parseInt(normalizedHex.slice(2, 4), 16)
  const blue = Number.parseInt(normalizedHex.slice(4, 6), 16)

  return { red, green, blue, alpha: 255 }
}

function loadImageElement(imageDataUrl: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const imageElement = new window.Image()
    imageElement.onload = () => resolve(imageElement)
    imageElement.onerror = reject
    imageElement.src = imageDataUrl
  })
}

function drawStrokeLineOnContext(
  context: CanvasRenderingContext2D,
  line: RelayDrawLine,
) {
  if (line.points.length === 0) return

  const firstPoint = line.points[0]

  context.save()
  context.lineCap = 'round'
  context.lineJoin = 'round'
  context.lineWidth = line.strokeWidth
  context.strokeStyle = line.color

  if (line.color === '#fffdf7') {
    context.globalCompositeOperation = 'destination-out'
  }

  context.beginPath()
  context.moveTo(firstPoint.x, firstPoint.y)

  line.points.slice(1).forEach((point) => {
    context.lineTo(point.x, point.y)
  })

  if (line.points.length === 1) {
    context.lineTo(firstPoint.x + 0.01, firstPoint.y + 0.01)
  }

  context.stroke()
  context.restore()
}

function drawFallbackFillOnContext(
  context: CanvasRenderingContext2D,
  line: RelayDrawLine,
) {
  if (line.points.length < 3) return

  const firstPoint = line.points[0]

  context.save()
  context.fillStyle = line.color
  context.beginPath()
  context.moveTo(firstPoint.x, firstPoint.y)

  line.points.slice(1).forEach((point) => {
    context.lineTo(point.x, point.y)
  })

  context.closePath()
  context.fill()
  context.restore()
}

async function drawLineOnContext(
  context: CanvasRenderingContext2D,
  line: RelayDrawLine,
) {
  if (line.kind === 'fill') {
    if (line.imageDataUrl) {
      const imageElement = await loadImageElement(line.imageDataUrl)
      context.drawImage(imageElement, 0, 0)
      return
    }

    drawFallbackFillOnContext(context, line)
    return
  }

  drawStrokeLineOnContext(context, line)
}

async function renderLinesToRasterCanvas(lines: RelayDrawLine[]) {
  const rasterCanvas = document.createElement('canvas')
  rasterCanvas.width = RELAY_STAGE_SIZE.width
  rasterCanvas.height = RELAY_STAGE_SIZE.height

  const rasterContext = rasterCanvas.getContext('2d')
  if (!rasterContext) return null

  for (const line of lines) {
    await drawLineOnContext(rasterContext, line)
  }

  return rasterCanvas
}

function isPixelMatchingTarget(
  imageData: Uint8ClampedArray,
  pixelOffset: number,
  targetColor: { red: number; green: number; blue: number; alpha: number },
) {
  const pixelAlpha = imageData[pixelOffset + 3]

  if (targetColor.alpha <= TRANSPARENT_ALPHA_TOLERANCE) {
    return pixelAlpha <= TRANSPARENT_ALPHA_TOLERANCE
  }

  return (
    Math.abs(imageData[pixelOffset] - targetColor.red) <= COLOR_MATCH_TOLERANCE &&
    Math.abs(imageData[pixelOffset + 1] - targetColor.green) <= COLOR_MATCH_TOLERANCE &&
    Math.abs(imageData[pixelOffset + 2] - targetColor.blue) <= COLOR_MATCH_TOLERANCE &&
    Math.abs(pixelAlpha - targetColor.alpha) <= COLOR_MATCH_TOLERANCE
  )
}

async function createBucketFillLine({
  activeRoundKey,
  fillColor,
  lines,
  pointerPosition,
}: {
  activeRoundKey: RelayRoundKey
  fillColor: string
  lines: RelayDrawLine[]
  pointerPosition: RelayDrawPoint
}) {
  const rasterCanvas = await renderLinesToRasterCanvas(lines)
  if (!rasterCanvas) return null

  const rasterContext = rasterCanvas.getContext('2d')
  if (!rasterContext) return null

  const canvasWidth = RELAY_STAGE_SIZE.width
  const canvasHeight = RELAY_STAGE_SIZE.height
  const seedX = Math.floor(pointerPosition.x)
  const seedY = Math.floor(pointerPosition.y)

  if (seedX < 0 || seedX >= canvasWidth || seedY < 0 || seedY >= canvasHeight) {
    return null
  }

  const sourceImageData = rasterContext.getImageData(0, 0, canvasWidth, canvasHeight)
  const sourcePixels = sourceImageData.data
  const seedPixelIndex = seedY * canvasWidth + seedX
  const seedPixelOffset = seedPixelIndex * 4
  const targetColor = {
    red: sourcePixels[seedPixelOffset],
    green: sourcePixels[seedPixelOffset + 1],
    blue: sourcePixels[seedPixelOffset + 2],
    alpha: sourcePixels[seedPixelOffset + 3],
  }
  const selectedFillColor = parseHexColor(fillColor)

  if (
    targetColor.alpha > TRANSPARENT_ALPHA_TOLERANCE &&
    Math.abs(targetColor.red - selectedFillColor.red) <= COLOR_MATCH_TOLERANCE &&
    Math.abs(targetColor.green - selectedFillColor.green) <= COLOR_MATCH_TOLERANCE &&
    Math.abs(targetColor.blue - selectedFillColor.blue) <= COLOR_MATCH_TOLERANCE
  ) {
    return null
  }

  const fillCanvas = document.createElement('canvas')
  fillCanvas.width = canvasWidth
  fillCanvas.height = canvasHeight

  const fillContext = fillCanvas.getContext('2d')
  if (!fillContext) return null

  const fillImageData = fillContext.createImageData(canvasWidth, canvasHeight)
  const fillPixels = fillImageData.data
  const visitedPixels = new Uint8Array(canvasWidth * canvasHeight)
  const pendingPixelIndexes = [seedPixelIndex]
  let filledPixelCount = 0

  while (pendingPixelIndexes.length > 0) {
    const currentPixelIndex = pendingPixelIndexes.pop()
    if (currentPixelIndex === undefined || visitedPixels[currentPixelIndex] === 1) {
      continue
    }

    visitedPixels[currentPixelIndex] = 1
    const currentPixelOffset = currentPixelIndex * 4

    if (!isPixelMatchingTarget(sourcePixels, currentPixelOffset, targetColor)) {
      continue
    }

    fillPixels[currentPixelOffset] = selectedFillColor.red
    fillPixels[currentPixelOffset + 1] = selectedFillColor.green
    fillPixels[currentPixelOffset + 2] = selectedFillColor.blue
    fillPixels[currentPixelOffset + 3] = 255
    filledPixelCount += 1

    const currentX = currentPixelIndex % canvasWidth
    const currentY = Math.floor(currentPixelIndex / canvasWidth)

    if (currentX > 0) pendingPixelIndexes.push(currentPixelIndex - 1)
    if (currentX < canvasWidth - 1) pendingPixelIndexes.push(currentPixelIndex + 1)
    if (currentY > 0) pendingPixelIndexes.push(currentPixelIndex - canvasWidth)
    if (currentY < canvasHeight - 1) {
      pendingPixelIndexes.push(currentPixelIndex + canvasWidth)
    }
  }

  if (filledPixelCount === 0) return null

  fillContext.putImageData(fillImageData, 0, 0)

  return {
    id: `${activeRoundKey}-fill-${Date.now()}-${lines.length}`,
    kind: 'fill' as const,
    color: fillColor,
    strokeWidth: 0,
    points: [],
    imageDataUrl: fillCanvas.toDataURL('image/png'),
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

      if (selectedToolKey === 'bucket') {
        void createBucketFillLine({
          activeRoundKey,
          fillColor: selectedColor,
          lines: activeRoundLines,
          pointerPosition,
        }).then((fillLine) => {
          if (!fillLine) return
          updateActiveRoundLines((currentLines) => [...currentLines, fillLine])
        })
        return
      }

      setIsDrawing(true)
      updateActiveRoundLines((currentLines) => [
        ...currentLines,
        {
          id: `${activeRoundKey}-line-${Date.now()}-${currentLines.length}`,
          kind: 'stroke',
          color: stageColor,
          strokeWidth: activeStrokeWidth,
          points: [{ x: pointerPosition.x, y: pointerPosition.y }],
        },
      ])
    },
    [
      activeRoundKey,
      activeRoundLines,
      activeStrokeWidth,
      selectedColor,
      selectedToolKey,
      stageColor,
      updateActiveRoundLines,
    ],
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
