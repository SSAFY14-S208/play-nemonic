const handledRouteRoomCodes = new Set<string>()
const dismissedFlipbookRoomCodes = new Set<string>()
let activeFlipbookRoomCode: string | null = null

type FlipbookHandledRouteRoomCodeWindow = Window & {
  __flipbookHandledRouteRoomCodes?: Set<string>
}

function getHandledRouteRoomCodes() {
  if (typeof window === 'undefined') return handledRouteRoomCodes

  const browserWindow = window as FlipbookHandledRouteRoomCodeWindow
  browserWindow.__flipbookHandledRouteRoomCodes ??= new Set<string>()

  return browserWindow.__flipbookHandledRouteRoomCodes
}

export function isDummyResultPreviewRoute() {
  if (typeof window === 'undefined') return false

  const searchParams = new URLSearchParams(window.location.search)

  return searchParams.get('dummyResult') === '1' || searchParams.get('mockResult') === '1'
}

export function markRouteRoomCodesHandled(...roomCodes: Array<string | null | undefined>) {
  roomCodes.forEach((roomCode) => {
    const normalizedRoomCode = roomCode?.trim().toUpperCase()
    if (normalizedRoomCode) {
      getHandledRouteRoomCodes().add(normalizedRoomCode)
    }
  })
}

export function hasRouteRoomCodeHandled(roomCode: string | null) {
  const normalizedRoomCode = roomCode?.trim().toUpperCase() ?? ''

  return normalizedRoomCode !== '' && getHandledRouteRoomCodes().has(normalizedRoomCode)
}

export function markFlipbookRoomDismissed(roomCode: string | null) {
  const normalizedRoomCode = roomCode?.trim().toUpperCase()
  if (!normalizedRoomCode) return

  dismissedFlipbookRoomCodes.add(normalizedRoomCode)
}

export function hasFlipbookRoomDismissed(roomCode: string | null) {
  const normalizedRoomCode = roomCode?.trim().toUpperCase() ?? ''

  return normalizedRoomCode !== '' && dismissedFlipbookRoomCodes.has(normalizedRoomCode)
}

export function resetFlipbookRoomDismissed(roomCode: string | null) {
  const normalizedRoomCode = roomCode?.trim().toUpperCase()
  if (!normalizedRoomCode) return

  dismissedFlipbookRoomCodes.delete(normalizedRoomCode)
}

export function getActiveFlipbookRoomCode() {
  return activeFlipbookRoomCode
}

export function setActiveFlipbookRoomCode(roomCode: string | null) {
  activeFlipbookRoomCode = roomCode
}

export function shouldIgnoreInactiveFlipbookRoom(roomCode: string) {
  const activeRoomCode = getActiveFlipbookRoomCode()
  const isDismissedInactiveRoom =
    hasFlipbookRoomDismissed(roomCode) && activeRoomCode !== roomCode
  const isDifferentActiveRoom = activeRoomCode !== null && activeRoomCode !== roomCode

  return isDismissedInactiveRoom || isDifferentActiveRoom
}
