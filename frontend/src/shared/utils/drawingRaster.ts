import type { DrawingBoardSize, DrawingLine } from '@/shared/types'

export function parseHexColor(hexColor: string) {
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

  if (
    line.compositeOperation === 'destination-out' ||
    (!line.compositeOperation && line.color === backgroundColor)
  ) {
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

export async function renderLinesToRasterCanvas({
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
