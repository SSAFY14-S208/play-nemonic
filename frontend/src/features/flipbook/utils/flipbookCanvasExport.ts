import type { DrawingLine } from '@/shared/types'
import { renderLinesToRasterCanvas } from '@/shared/utils'
import {
  FLIPBOOK_BACKGROUND_COLOR,
  FLIPBOOK_BOARD_SIZE,
} from '../constants'

export const FLIPBOOK_FILE_CONTENT_TYPE = 'image/png'
export const FLIPBOOK_FILE_PURPOSE = 'FLIPBOOK'

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
