const INFINITE_CANVAS_ROOM_BASE_PATH = '/infinite-canvas'

export function buildInfiniteCanvasRoomPath(canvasId: string) {
  return `${INFINITE_CANVAS_ROOM_BASE_PATH}/${encodeURIComponent(canvasId)}`
}
