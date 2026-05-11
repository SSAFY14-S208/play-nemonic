import type { DrawingLine } from '@/shared/types'
import { renderLinesToRasterCanvas } from '@/shared/utils'
import {
  FLIPBOOK_BACKGROUND_COLOR,
  FLIPBOOK_BOARD_SIZE,
} from '../constants'

export const FLIPBOOK_FILE_CONTENT_TYPE = 'image/png'
export const FLIPBOOK_FILE_PURPOSE = 'FLIPBOOK_FRAME'

export async function createCanvasBlobFromLines(lines: DrawingLine[]) {
  const renderedCanvas = await renderLinesToRasterCanvas({
    backgroundColor: FLIPBOOK_BACKGROUND_COLOR,
    boardSize: FLIPBOOK_BOARD_SIZE,
    lines,
  })
  const rasterCanvas = renderedCanvas ?? document.createElement('canvas')

  if (!renderedCanvas) {
    rasterCanvas.width = FLIPBOOK_BOARD_SIZE.width
    rasterCanvas.height = FLIPBOOK_BOARD_SIZE.height
  }
  const context = rasterCanvas.getContext('2d')

  if (context) {
    context.save()
    context.globalCompositeOperation = 'destination-over'
    context.fillStyle = FLIPBOOK_BACKGROUND_COLOR
    context.fillRect(0, 0, rasterCanvas.width, rasterCanvas.height)
    context.restore()
  }

  return new Promise<Blob>((resolve, reject) => {
    rasterCanvas.toBlob((blob) => {
      if (blob) {
        resolve(blob)
        return
      }

      reject(new Error('플립북 프레임 이미지를 만들 수 없습니다.'))
    }, FLIPBOOK_FILE_CONTENT_TYPE)
  })
}
