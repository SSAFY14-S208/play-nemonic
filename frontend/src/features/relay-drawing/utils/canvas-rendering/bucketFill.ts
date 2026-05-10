// Scanline-style flood fill — 페인트 버킷 도구의 핵심 알고리즘.
// 시드 픽셀과 색이 같은(허용 오차 내) 인접 픽셀을 BFS로 채운 뒤 결과를
// dataURL로 변환해 line으로 반환한다. 이렇게 dataURL 기반의 line으로 두면
// 일반 stroke line과 같은 배열에 섞여 들어가도 스토어 모델이 단일하게 유지된다.

import { RELAY_ROUND_RULES, RELAY_STAGE_SIZE, type RelayRoundKey } from '../../constants'
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
  // 라운드별로 canvas 높이가 다르다 (face=720, body/legs=840). raster를 정확한
  // 크기로 만들어야 BFS가 hint zone까지 포함한 영역을 올바르게 다룬다.
  const roundCanvasHeight = RELAY_ROUND_RULES[activeRoundKey].canvasHeight
  const rasterCanvas = await renderLinesToRasterCanvas(lines, roundCanvasHeight)
  if (!rasterCanvas) return null

  const rasterContext = rasterCanvas.getContext('2d')
  if (!rasterContext) return null

  const canvasWidth = RELAY_STAGE_SIZE.width
  const canvasHeight = roundCanvasHeight
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

  // ── Post-fill dilation ─────────────────────────────────────────────
  // BFS는 시드 색과 매칭되는 픽셀만 채우므로 라인 자체(불투명한 stroke)와 그
  // anti-aliased 가장자리(halo)는 빈 자리로 남는다. dilation은 두 가지 픽셀을
  // 흡수해 fill을 깔끔하게 만든다:
  //
  //   1) **halo (alpha 1~254)** — 라인 가장자리의 anti-alias 그라디언트. 흡수하지
  //      않으면 fill 외곽에 가는 흰 띠로 보인다. fill neighbor가 1개만 있어도 흡수.
  //   2) **고립된 alpha-255 점 (1~2px speckle)** — 사용자가 부주의하게 클릭/탭한
  //      자국. fill 영역 한가운데 흰/유색 점으로 보인다. fill neighbor가 3개 이상
  //      (즉 사방이 거의 다 fill로 둘러싸인 작은 섬)일 때만 흡수.
  //
  // 보존되는 케이스 — **연속된 라인 코어**(닫힌 boundary, 라인 중앙). 이런 픽셀은
  // 양쪽에 인접 라인 픽셀이 있어 fill neighbor가 0~2개라 흡수 조건(>=3) 미달.
  // 결과: 사용자가 그린 outline은 그대로 보이고, 우연한 스펙클만 사라진다.
  //
  // target이 불투명 색(이전 fill 위에 다시 fill)이면 색상 거리 기준 분기.
  //
  // strokeWidth가 두꺼우면 halo가 3~5px까지 깊어지므로 6회 반복.
  const DILATION_PASSES = 6
  const DILATION_COLOR_TOLERANCE = 96
  const isTransparentTarget = targetColor.alpha <= TRANSPARENT_ALPHA_TOLERANCE

  for (let dilationPass = 0; dilationPass < DILATION_PASSES; dilationPass++) {
    const newlyFilledIndexes: number[] = []
    for (let pixelIndex = 0; pixelIndex < canvasWidth * canvasHeight; pixelIndex++) {
      const pixelOffset = pixelIndex * 4
      // 이미 채워진 픽셀은 스킵 (visitedPixels는 BFS rejected까지 포함하므로
      // fillPixels의 alpha=255 여부를 진실의 기준으로 본다).
      if (fillPixels[pixelOffset + 3] === 255) continue

      const currentX = pixelIndex % canvasWidth
      const currentY = Math.floor(pixelIndex / canvasWidth)
      let filledNeighborCount = 0
      if (currentX > 0 && fillPixels[(pixelIndex - 1) * 4 + 3] === 255) {
        filledNeighborCount += 1
      }
      if (
        currentX < canvasWidth - 1 &&
        fillPixels[(pixelIndex + 1) * 4 + 3] === 255
      ) {
        filledNeighborCount += 1
      }
      if (
        currentY > 0 &&
        fillPixels[(pixelIndex - canvasWidth) * 4 + 3] === 255
      ) {
        filledNeighborCount += 1
      }
      if (
        currentY < canvasHeight - 1 &&
        fillPixels[(pixelIndex + canvasWidth) * 4 + 3] === 255
      ) {
        filledNeighborCount += 1
      }
      if (filledNeighborCount === 0) continue

      const sourceAlpha = sourcePixels[pixelOffset + 3]
      const isHaloCandidate = isTransparentTarget
        ? // halo: alpha 1~254는 인접 fill 1개만 있어도 흡수.
          // 고립된 alpha-255 점: 사방의 3+ 면이 fill로 둘러싸인 1~2px 섬만 흡수.
          // 연속 라인 코어는 인접 라인 픽셀 때문에 fill neighbor가 2개를 넘지 못함.
          sourceAlpha < 255 || filledNeighborCount >= 3
        : // 불투명 target: 색상이 target에 느슨한 거리 안에 있으면 halo로 본다.
          Math.abs(sourcePixels[pixelOffset] - targetColor.red) <=
            DILATION_COLOR_TOLERANCE &&
          Math.abs(sourcePixels[pixelOffset + 1] - targetColor.green) <=
            DILATION_COLOR_TOLERANCE &&
          Math.abs(sourcePixels[pixelOffset + 2] - targetColor.blue) <=
            DILATION_COLOR_TOLERANCE

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
      fillPixels[dilatedPixelOffset + 3] = 255
      filledPixelCount += 1
    }
  }

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
