import type { RelayRoundKey } from '../constants'
import type { RelayDrawLine, RelayDrawPoint, RelayRoundLines } from '..'

export const appendLineToRound = (
  roundLines: RelayRoundLines,
  roundKey: RelayRoundKey,
  line: RelayDrawLine,
) => ({
  ...roundLines,
  [roundKey]: [...roundLines[roundKey], line],
})

export const appendPointToLatestLine = (
  roundLines: RelayRoundLines,
  roundKey: RelayRoundKey,
  point: RelayDrawPoint,
): RelayRoundLines | null => {
  const currentLines = roundLines[roundKey]
  const latestLine = currentLines[currentLines.length - 1]
  if (!latestLine) return null

  const updatedLine: RelayDrawLine = {
    ...latestLine,
    points: [...latestLine.points, point],
  }

  return {
    ...roundLines,
    [roundKey]: [...currentLines.slice(0, -1), updatedLine],
  }
}

export const moveLatestLineToRedoStack = (
  roundLines: RelayRoundLines,
  roundRedoStack: RelayRoundLines,
  roundKey: RelayRoundKey,
) => {
  const currentLines = roundLines[roundKey]
  if (currentLines.length === 0) return null

  const poppedLine = currentLines[currentLines.length - 1]

  return {
    roundLines: {
      ...roundLines,
      [roundKey]: currentLines.slice(0, -1),
    },
    roundRedoStack: {
      ...roundRedoStack,
      [roundKey]: [...roundRedoStack[roundKey], poppedLine],
    },
  }
}

export const restoreLatestRedoLine = (
  roundLines: RelayRoundLines,
  roundRedoStack: RelayRoundLines,
  roundKey: RelayRoundKey,
) => {
  const currentRedoStack = roundRedoStack[roundKey]
  if (currentRedoStack.length === 0) return null

  const restoredLine = currentRedoStack[currentRedoStack.length - 1]

  return {
    roundLines: {
      ...roundLines,
      [roundKey]: [...roundLines[roundKey], restoredLine],
    },
    roundRedoStack: {
      ...roundRedoStack,
      [roundKey]: currentRedoStack.slice(0, -1),
    },
  }
}

export const clearRoundDrawing = (
  roundLines: RelayRoundLines,
  roundRedoStack: RelayRoundLines,
  roundKey: RelayRoundKey,
) => ({
  roundLines: {
    ...roundLines,
    [roundKey]: [],
  },
  roundRedoStack: {
    ...roundRedoStack,
    [roundKey]: [],
  },
})
