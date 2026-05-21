import type { DrawingBoardSize, DrawingLine, DrawingPoint } from '@/shared/types'
import { parseHexColor, renderLinesToRasterCanvas } from './drawingRaster'

const TRANSPARENT_ALPHA_TOLERANCE = 16
const COLOR_MATCH_TOLERANCE = 12
const DILATION_PASSES = 6
const DILATION_COLOR_TOLERANCE = 96
const STROKE_EDGE_FILL_PASSES = 6

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
  idPrefix = 'fill',
  lines,
  pointerPosition,
}: {
  backgroundColor: string
  boardSize: DrawingBoardSize
  fillColor: string
  fillOpacity?: number
  idPrefix?: string
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

  const isTransparentTarget = targetColor.alpha <= TRANSPARENT_ALPHA_TOLERANCE

  for (let dilationPass = 0; dilationPass < DILATION_PASSES; dilationPass++) {
    const newlyFilledIndexes: number[] = []

    for (let pixelIndex = 0; pixelIndex < canvasWidth * canvasHeight; pixelIndex++) {
      const pixelOffset = pixelIndex * 4
      if (fillPixels[pixelOffset + 3] === selectedFillAlpha) continue

      const currentX = pixelIndex % canvasWidth
      const currentY = Math.floor(pixelIndex / canvasWidth)
      let filledNeighborCount = 0

      if (currentX > 0 && fillPixels[(pixelIndex - 1) * 4 + 3] === selectedFillAlpha) {
        filledNeighborCount += 1
      }
      if (
        currentX < canvasWidth - 1 &&
        fillPixels[(pixelIndex + 1) * 4 + 3] === selectedFillAlpha
      ) {
        filledNeighborCount += 1
      }
      if (currentY > 0 && fillPixels[(pixelIndex - canvasWidth) * 4 + 3] === selectedFillAlpha) {
        filledNeighborCount += 1
      }
      if (
        currentY < canvasHeight - 1 &&
        fillPixels[(pixelIndex + canvasWidth) * 4 + 3] === selectedFillAlpha
      ) {
        filledNeighborCount += 1
      }

      if (filledNeighborCount === 0) continue

      const sourceAlpha = sourcePixels[pixelOffset + 3]
      const isHaloCandidate = isTransparentTarget
        ? sourceAlpha < 255 || filledNeighborCount >= 3
        : Math.abs(sourcePixels[pixelOffset] - targetColor.red) <= DILATION_COLOR_TOLERANCE &&
          Math.abs(sourcePixels[pixelOffset + 1] - targetColor.green) <=
            DILATION_COLOR_TOLERANCE &&
          Math.abs(sourcePixels[pixelOffset + 2] - targetColor.blue) <= DILATION_COLOR_TOLERANCE

      if (isHaloCandidate) {
        newlyFilledIndexes.push(pixelIndex)
      }
    }

    if (newlyFilledIndexes.length === 0) break

    for (const dilatedPixelIndex of newlyFilledIndexes) {
      const dilatedPixelOffset = dilatedPixelIndex * 4
      fillPixels[dilatedPixelOffset] = selectedFillColor.red
      fillPixels[dilatedPixelOffset + 1] = selectedFillColor.green
      fillPixels[dilatedPixelOffset + 2] = selectedFillColor.blue
      fillPixels[dilatedPixelOffset + 3] = selectedFillAlpha
      filledPixelCount += 1
    }
  }

  if (isTransparentTarget) {
    for (let edgeFillPass = 0; edgeFillPass < STROKE_EDGE_FILL_PASSES; edgeFillPass++) {
      const newlyFilledIndexes: number[] = []

      for (let pixelIndex = 0; pixelIndex < canvasWidth * canvasHeight; pixelIndex++) {
        const pixelOffset = pixelIndex * 4
        if (fillPixels[pixelOffset + 3] === selectedFillAlpha) continue
        if (sourcePixels[pixelOffset + 3] <= TRANSPARENT_ALPHA_TOLERANCE) continue

        const currentX = pixelIndex % canvasWidth
        const currentY = Math.floor(pixelIndex / canvasWidth)
        let filledNeighborCount = 0

        if (currentX > 0 && fillPixels[(pixelIndex - 1) * 4 + 3] === selectedFillAlpha) {
          filledNeighborCount += 1
        }
        if (
          currentX < canvasWidth - 1 &&
          fillPixels[(pixelIndex + 1) * 4 + 3] === selectedFillAlpha
        ) {
          filledNeighborCount += 1
        }
        if (
          currentY > 0 &&
          fillPixels[(pixelIndex - canvasWidth) * 4 + 3] === selectedFillAlpha
        ) {
          filledNeighborCount += 1
        }
        if (
          currentY < canvasHeight - 1 &&
          fillPixels[(pixelIndex + canvasWidth) * 4 + 3] === selectedFillAlpha
        ) {
          filledNeighborCount += 1
        }

        if (filledNeighborCount > 0) {
          newlyFilledIndexes.push(pixelIndex)
        }
      }

      if (newlyFilledIndexes.length === 0) break

      for (const edgePixelIndex of newlyFilledIndexes) {
        const edgePixelOffset = edgePixelIndex * 4
        fillPixels[edgePixelOffset] = selectedFillColor.red
        fillPixels[edgePixelOffset + 1] = selectedFillColor.green
        fillPixels[edgePixelOffset + 2] = selectedFillColor.blue
        fillPixels[edgePixelOffset + 3] = selectedFillAlpha
        filledPixelCount += 1
      }
    }
  }

  fillContext.putImageData(fillImageData, 0, 0)

  return {
    id: `${idPrefix}-${Date.now()}-${lines.length}`,
    kind: 'fill' as const,
    color: fillColor,
    strokeWidth: 0,
    opacity: 1,
    compositeOperation: isTransparentTarget
      ? ('destination-over' as const)
      : ('source-over' as const),
    points: [],
    imageDataUrl: fillCanvas.toDataURL('image/png'),
  }
}
