import type { DrawingBoardSize, DrawingLine } from '@/shared/types'
import { renderLinesToRasterCanvas } from '@/shared/utils'

export const COMMUNITY_SNAPSHOT_CONTENT_TYPE = 'image/png'

const ORIGINAL_WIDTH = 840
const ORIGINAL_HEIGHT = 630
const THUMBNAIL_WIDTH = 360
const THUMBNAIL_HEIGHT = 270
const BACKGROUND_REMOVE_HARD_BRIGHTNESS = 238
const BACKGROUND_REMOVE_SOFT_BRIGHTNESS = 214
const BACKGROUND_REMOVE_MAX_CHANNEL_SPREAD = 48

interface DirectCommunitySnapshotOptions {
  boardSize: DrawingBoardSize
  lines: DrawingLine[]
  backgroundColor: string
}

interface GalleryCommunitySnapshotOptions {
  imageUrl: string
  boardSize: DrawingBoardSize
  lines: DrawingLine[]
  backgroundColor: string
}

interface CommunitySnapshotBlobs {
  originalBlob: Blob
  thumbnailBlob: Blob
}

function createCanvas(width: number, height: number) {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  return canvas
}

function canvasToBlob(canvas: HTMLCanvasElement) {
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error('snapshot-blob-empty'))
        return
      }

      resolve(blob)
    }, COMMUNITY_SNAPSHOT_CONTENT_TYPE)
  })
}

function createThumbnailBlob(originalCanvas: HTMLCanvasElement) {
  const thumbnailCanvas = createCanvas(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
  const thumbnailContext = thumbnailCanvas.getContext('2d')
  if (!thumbnailContext) throw new Error('snapshot-context-unavailable')

  thumbnailContext.drawImage(
    originalCanvas,
    0,
    0,
    originalCanvas.width,
    originalCanvas.height,
    0,
    0,
    thumbnailCanvas.width,
    thumbnailCanvas.height,
  )

  return canvasToBlob(thumbnailCanvas)
}

function loadImageElement(imageUrl: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const imageElement = new window.Image()
    imageElement.crossOrigin = 'anonymous'
    imageElement.onload = () => resolve(imageElement)
    imageElement.onerror = () => reject(new Error('snapshot-image-load-failed'))
    imageElement.src = imageUrl
  })
}

function drawContainImage({
  context,
  imageElement,
  x,
  y,
  width,
  height,
}: {
  context: CanvasRenderingContext2D
  imageElement: HTMLImageElement
  x: number
  y: number
  width: number
  height: number
}) {
  const imageRatio = imageElement.naturalWidth / imageElement.naturalHeight
  const frameRatio = width / height
  const drawWidth = imageRatio > frameRatio ? width : height * imageRatio
  const drawHeight = imageRatio > frameRatio ? width / imageRatio : height
  const drawX = x + (width - drawWidth) / 2
  const drawY = y + (height - drawHeight) / 2

  context.drawImage(imageElement, drawX, drawY, drawWidth, drawHeight)
}

function getPixelLuma(red: number, green: number, blue: number) {
  return red * 0.299 + green * 0.587 + blue * 0.114
}

function getPixelSaturationSpread(red: number, green: number, blue: number) {
  return Math.max(red, green, blue) - Math.min(red, green, blue)
}

function getBackgroundAlphaMultiplier(red: number, green: number, blue: number) {
  const luma = getPixelLuma(red, green, blue)
  const spread = getPixelSaturationSpread(red, green, blue)

  if (spread > BACKGROUND_REMOVE_MAX_CHANNEL_SPREAD) return 1
  if (luma >= BACKGROUND_REMOVE_HARD_BRIGHTNESS) return 0
  if (luma <= BACKGROUND_REMOVE_SOFT_BRIGHTNESS) return 1

  return (BACKGROUND_REMOVE_HARD_BRIGHTNESS - luma) /
    (BACKGROUND_REMOVE_HARD_BRIGHTNESS - BACKGROUND_REMOVE_SOFT_BRIGHTNESS)
}

function removeLightBackgroundFromCanvas(canvas: HTMLCanvasElement) {
  const context = canvas.getContext('2d')
  if (!context) return

  const imageData = context.getImageData(0, 0, canvas.width, canvas.height)
  const pixels = imageData.data

  for (let index = 0; index < pixels.length; index += 4) {
    const red = pixels[index]
    const green = pixels[index + 1]
    const blue = pixels[index + 2]
    const alpha = pixels[index + 3]
    if (alpha === 0) continue

    pixels[index + 3] = Math.round(alpha * getBackgroundAlphaMultiplier(red, green, blue))
  }

  context.putImageData(imageData, 0, 0)
}

export function hasDirectSnapshotContent(lines: DrawingLine[]) {
  return lines.length > 0
}

export async function exportDirectCommunitySnapshot({
  boardSize,
  lines,
  backgroundColor,
}: DirectCommunitySnapshotOptions): Promise<CommunitySnapshotBlobs> {
  const originalCanvas = createCanvas(ORIGINAL_WIDTH, ORIGINAL_HEIGHT)
  const context = originalCanvas.getContext('2d')
  if (!context) throw new Error('snapshot-context-unavailable')

  const rasterCanvas = await renderLinesToRasterCanvas({
    backgroundColor,
    boardSize,
    lines,
  })

  if (rasterCanvas) {
    context.drawImage(rasterCanvas, 0, 0, originalCanvas.width, originalCanvas.height)
  }
  removeLightBackgroundFromCanvas(originalCanvas)

  return {
    originalBlob: await canvasToBlob(originalCanvas),
    thumbnailBlob: await createThumbnailBlob(originalCanvas),
  }
}

export async function exportGalleryCommunitySnapshot({
  imageUrl,
  boardSize,
  lines,
  backgroundColor,
}: GalleryCommunitySnapshotOptions): Promise<CommunitySnapshotBlobs> {
  const originalCanvas = createCanvas(ORIGINAL_WIDTH, ORIGINAL_HEIGHT)
  const context = originalCanvas.getContext('2d')
  if (!context) throw new Error('snapshot-context-unavailable')

  const imageElement = await loadImageElement(imageUrl)

  drawContainImage({
    context,
    imageElement,
    x: 0,
    y: 0,
    width: originalCanvas.width,
    height: originalCanvas.height,
  })

  if (lines.length > 0) {
    const rasterCanvas = await renderLinesToRasterCanvas({
      backgroundColor,
      boardSize,
      lines,
    })

    if (rasterCanvas) {
      context.drawImage(rasterCanvas, 0, 0, originalCanvas.width, originalCanvas.height)
    }
  }

  removeLightBackgroundFromCanvas(originalCanvas)

  return {
    originalBlob: await canvasToBlob(originalCanvas),
    thumbnailBlob: await createThumbnailBlob(originalCanvas),
  }
}
