// Scanline-style flood fill — 페인트 버킷 도구의 핵심 알고리즘.
// 시드 픽셀과 색이 같은(허용 오차 내) 인접 픽셀을 BFS로 채운 뒤 결과를
// dataURL로 변환해 line으로 반환한다. 이렇게 dataURL 기반의 line으로 두면
// 일반 stroke line과 같은 배열에 섞여 들어가도 스토어 모델이 단일하게 유지된다.

import { RELAY_STAGE_SIZE, type RelayRoundKey } from '../../constants'
import type { RelayDrawLine, RelayDrawPoint } from '../../types'

import {
  COLOR_MATCH_TOLERANCE,
  TRANSPARENT_ALPHA_TOLERANCE,
  isPixelMatchingTarget,
  parseHexColor,
} from './color'
import { renderLinesToRasterCanvas } from './rasterizer'

interface CreateBucketFillLineArgs {
  activeRoundKey: RelayRoundKey
  fillColor: string
  lines: RelayDrawLine[]
  pointerPosition: RelayDrawPoint
}

export async function createBucketFillLine({
  activeRoundKey,
  fillColor,
  lines,
  pointerPosition,
}: CreateBucketFillLineArgs) {
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

  // 시드가 이미 같은 색이면 작업할 게 없다 — early exit.
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
