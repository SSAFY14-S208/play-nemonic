// 라인/필 데이터를 raster Canvas로 옮기는 그리기 프리미티브 모음.
// bucket fill이 시드 픽셀을 읽기 위해 캔버스 raster 표현이 필요해서 분리.
// 또한 round transition 애니메이션이 같은 raster를 재사용한다.

import { RELAY_STAGE_SIZE } from '../../constants'
import type { RelayDrawLine } from '../../types'

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

  // 캔버스 배경색과 같은 흰색이면 지우개로 동작 — destination-out이면 알파를 비운다.
  if (line.color === '#fffdf7') {
    context.globalCompositeOperation = 'destination-out'
  }

  context.beginPath()
  context.moveTo(firstPoint.x, firstPoint.y)

  line.points.slice(1).forEach((point) => {
    context.lineTo(point.x, point.y)
  })

  // 점 1개짜리 클릭은 lineTo가 안 걸려서 stroke가 안 나온다 — 미세하게 어긋난 점을
  // 넣어 0-length 세그먼트를 보강한다.
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

// 한 라운드의 모든 라인을 RELAY_STAGE_SIZE 크기 raster Canvas에 합성한다.
// bucket fill 시드 검사 / 라운드 전환 애니메이션 / 결과 합성이 공통으로 사용한다.
export async function renderLinesToRasterCanvas(lines: RelayDrawLine[]) {
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
