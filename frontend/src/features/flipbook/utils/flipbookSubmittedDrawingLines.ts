import type { DrawingLine, FlipbookAssignmentResponse } from '@/shared/types'

const SUBMITTED_DRAWING_LINES_STORAGE_KEY = 'flipbook-submitted-drawing-lines:v1'

export function getSubmittedDrawingLinesKey({
  assignment,
  roomCode,
  userUuid,
}: {
  assignment: FlipbookAssignmentResponse
  roomCode: string
  userUuid: string | null
}) {
  return [
    roomCode,
    userUuid ?? 'anonymous',
    assignment.currentRound,
    assignment.flipbookIndex,
    assignment.frameIndex,
  ].join(':')
}

export function readSubmittedDrawingLines(
  storageKey: string,
): DrawingLine[] | null {
  if (typeof window === 'undefined') return null

  try {
    const rawStorageValue = window.localStorage.getItem(SUBMITTED_DRAWING_LINES_STORAGE_KEY)
    if (!rawStorageValue) return null

    const storedDrawingLinesByKey = JSON.parse(rawStorageValue) as Record<string, DrawingLine[]>
    const storedDrawingLines = storedDrawingLinesByKey[storageKey]

    return Array.isArray(storedDrawingLines) ? storedDrawingLines : null
  } catch {
    return null
  }
}

export function writeSubmittedDrawingLines(storageKey: string, lines: DrawingLine[]) {
  if (typeof window === 'undefined') return

  try {
    const rawStorageValue = window.localStorage.getItem(SUBMITTED_DRAWING_LINES_STORAGE_KEY)
    const storedDrawingLinesByKey = rawStorageValue
      ? (JSON.parse(rawStorageValue) as Record<string, DrawingLine[]>)
      : {}

    window.localStorage.setItem(
      SUBMITTED_DRAWING_LINES_STORAGE_KEY,
      JSON.stringify({
        ...storedDrawingLinesByKey,
        [storageKey]: lines,
      }),
    )
  } catch {
    // Waiting preview is best-effort; upload/submission remains the source of truth.
  }
}

export function clearSubmittedDrawingLinesStorage() {
  if (typeof window === 'undefined') return

  try {
    window.localStorage.removeItem(SUBMITTED_DRAWING_LINES_STORAGE_KEY)
  } catch {
    // Local drawing backup is disposable; ignore restricted storage failures.
  }
}
