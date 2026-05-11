import type { DrawingBoardSize, DrawingLine, DrawingPoint } from '@/shared/types'
import { parseHexColor, renderLinesToRasterCanvas } from './drawingRaster'

const TRANSPARENT_ALPHA_TOLERANCE = 16
const COLOR_MATCH_TOLERANCE = 12

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

export async function createBucketFillLine({
  backgroundColor,
  boardSize,
  fillColor,
  fillOpacity = 1,
  lines,
  pointerPosition,
}: {
  backgroundColor: string
  boardSize: DrawingBoardSize
  fillColor: string
  fillOpacity?: number
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
  const selectedFillAlpha = Math.round(Math.min(Math.max(fillOpacity, 0), 1) * 255)

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
    fillPixels[currentPixelOffset + 3] = selectedFillAlpha
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
    opacity: 1,
    points: [],
    imageDataUrl: fillCanvas.toDataURL('image/png'),
  }
}
