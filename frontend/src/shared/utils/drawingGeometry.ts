import type { DrawingArea, DrawingBoardSize, DrawingPoint } from '@/shared/types'

export function isPointInsideDrawingArea(
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
