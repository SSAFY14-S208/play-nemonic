'use client'

import { useCallback, useState } from 'react'
import type {
  DrawingArea,
  DrawingBoardSize,
  DrawingLine,
  DrawingPoint,
  DrawingPointerEvent,
  DrawingToolKey,
} from '@/shared/types'

interface UseDrawingBoardOptions {
  boardSize: DrawingBoardSize
  drawArea?: DrawingArea
  backgroundColor?: string
  defaultColor: string
  defaultStrokeWidth?: number
}

const TRANSPARENT_ALPHA_TOLERANCE = 16
const COLOR_MATCH_TOLERANCE = 12

function isPointInsideArea(
  point: DrawingPoint,
  boardSize: DrawingBoardSize,
  drawArea?: DrawingArea,
) {
  const activeDrawArea = drawArea ?? { y: 0, height: boardSize.height }

  return (
    point.x >= 0 &&
    point.x <= boardSize.width &&
    point.y >= activeDrawArea.y &&
    point.y <= activeDrawArea.y + activeDrawArea.height
  )
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
  line: DrawingLine,
  backgroundColor: string,
) {
  if (line.points.length === 0) return

  const firstPoint = line.points[0]

  context.save()
  context.lineCap = 'round'
  context.lineJoin = 'round'
  context.lineWidth = line.strokeWidth
  context.strokeStyle = line.color

  if (line.color === backgroundColor) {
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

function drawFallbackFillOnContext(context: CanvasRenderingContext2D, line: DrawingLine) {
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
  line: DrawingLine,
  backgroundColor: string,
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

  drawStrokeLineOnContext(context, line, backgroundColor)
}

async function renderLinesToRasterCanvas({
  backgroundColor,
  boardSize,
  lines,
}: {
  backgroundColor: string
  boardSize: DrawingBoardSize
  lines: DrawingLine[]
}) {
  const rasterCanvas = document.createElement('canvas')
  rasterCanvas.width = boardSize.width
  rasterCanvas.height = boardSize.height

  const rasterContext = rasterCanvas.getContext('2d')
  if (!rasterContext) return null

  for (const line of lines) {
    await drawLineOnContext(rasterContext, line, backgroundColor)
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
  backgroundColor,
  boardSize,
  fillColor,
  lines,
  pointerPosition,
}: {
  backgroundColor: string
  boardSize: DrawingBoardSize
  fillColor: string
  lines: DrawingLine[]
  pointerPosition: DrawingPoint
}) {
  const rasterCanvas = await renderLinesToRasterCanvas({ backgroundColor, boardSize, lines })
  if (!rasterCanvas) return null

  const rasterContext = rasterCanvas.getContext('2d')
  if (!rasterContext) return null

  const canvasWidth = boardSize.width
  const canvasHeight = boardSize.height
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
    if (currentY < canvasHeight - 1) pendingPixelIndexes.push(currentPixelIndex + canvasWidth)
  }

  if (filledPixelCount === 0) return null

  fillContext.putImageData(fillImageData, 0, 0)

  return {
    id: `fill-${Date.now()}-${lines.length}`,
    kind: 'fill' as const,
    color: fillColor,
    strokeWidth: 0,
    points: [],
    imageDataUrl: fillCanvas.toDataURL('image/png'),
  }
}

export function useDrawingBoard({
  boardSize,
  drawArea,
  backgroundColor = '#fffdf7',
  defaultColor,
  defaultStrokeWidth = 4,
}: UseDrawingBoardOptions) {
  const [selectedToolKey, setSelectedToolKey] = useState<DrawingToolKey>('pencil')
  const [selectedColor, setSelectedColor] = useState(defaultColor)
  const [strokeWidth, setStrokeWidth] = useState(defaultStrokeWidth)
  const [lines, setLines] = useState<DrawingLine[]>([])
  const [redoLines, setRedoLines] = useState<DrawingLine[]>([])
  const [isDrawing, setIsDrawing] = useState(false)

  const drawingColor = selectedToolKey === 'eraser' ? backgroundColor : selectedColor
  const activeStrokeWidth = selectedToolKey === 'marker' ? strokeWidth + 4 : strokeWidth

  const clearDrawing = useCallback(() => {
    setRedoLines((currentRedoLines) => [...lines, ...currentRedoLines])
    setLines([])
  }, [lines])

  const undoDrawing = useCallback(() => {
    setLines((currentLines) => {
      const latestLine = currentLines[currentLines.length - 1]
      if (!latestLine) return currentLines
      setRedoLines((currentRedoLines) => [latestLine, ...currentRedoLines])
      return currentLines.slice(0, -1)
    })
  }, [])

  const redoDrawing = useCallback(() => {
    setRedoLines((currentRedoLines) => {
      const nextLine = currentRedoLines[0]
      if (!nextLine) return currentRedoLines
      setLines((currentLines) => [...currentLines, nextLine])
      return currentRedoLines.slice(1)
    })
  }, [])

  const replaceLines = useCallback((nextLines: DrawingLine[]) => {
    setLines(nextLines)
    setRedoLines([])
    setIsDrawing(false)
  }, [])

  const beginDrawing = useCallback(
    (event: DrawingPointerEvent) => {
      const stage = event.target.getStage()
      const pointerPosition = stage?.getPointerPosition()
      if (!pointerPosition) return
      if (!isPointInsideArea(pointerPosition, boardSize, drawArea)) return

      if (selectedToolKey === 'bucket') {
        void createBucketFillLine({
          backgroundColor,
          boardSize,
          fillColor: selectedColor,
          lines,
          pointerPosition,
        }).then((fillLine) => {
          if (!fillLine) return
          setRedoLines([])
          setLines((currentLines) => [...currentLines, fillLine])
        })
        return
      }

      setIsDrawing(true)
      setRedoLines([])
      setLines((currentLines) => [
        ...currentLines,
        {
          id: `line-${Date.now()}-${currentLines.length}`,
          kind: 'stroke',
          color: drawingColor,
          strokeWidth: activeStrokeWidth,
          points: [{ x: pointerPosition.x, y: pointerPosition.y }],
        },
      ])
    },
    [
      activeStrokeWidth,
      backgroundColor,
      boardSize,
      drawArea,
      drawingColor,
      lines,
      selectedColor,
      selectedToolKey,
    ],
  )

  const continueDrawing = useCallback(
    (event: DrawingPointerEvent) => {
      if (!isDrawing) return

      const stage = event.target.getStage()
      const pointerPosition = stage?.getPointerPosition()
      if (!pointerPosition) return

      if (!isPointInsideArea(pointerPosition, boardSize, drawArea)) {
        setIsDrawing(false)
        return
      }

      setLines((currentLines) => {
        const latestLine = currentLines[currentLines.length - 1]
        if (!latestLine) return currentLines

        const updatedLine: DrawingLine = {
          ...latestLine,
          points: [...latestLine.points, { x: pointerPosition.x, y: pointerPosition.y }],
        }

        return [...currentLines.slice(0, -1), updatedLine]
      })
    },
    [boardSize, drawArea, isDrawing],
  )

  const endDrawing = useCallback(() => {
    setIsDrawing(false)
  }, [])

  return {
    selectedToolKey,
    selectedColor,
    strokeWidth,
    lines,
    canRedoDrawing: redoLines.length > 0,
    replaceLines,
    setLines,
    setSelectedToolKey,
    setSelectedColor,
    setStrokeWidth,
    clearDrawing,
    undoDrawing,
    redoDrawing,
    beginDrawing,
    continueDrawing,
    endDrawing,
  }
}
