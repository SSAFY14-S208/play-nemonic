import type { StateCreator } from 'zustand'

import { RELAY_COLORS, RELAY_ROUND_ORDER } from '../constants'
import type { RelayDrawLine, RelayRoundLines } from '../types'

import type { CanvasSlice, RelayDrawingStore } from './store.types'

const DEFAULT_STROKE_WIDTH = 4
const DEFAULT_ROUND_LINES: RelayRoundLines = {
  face: [],
  body: [],
  legs: [],
}

export const createCanvasSlice: StateCreator<RelayDrawingStore, [], [], CanvasSlice> = (
  set,
  get,
) => ({
  activeRoundKey: 'face',
  selectedToolKey: 'pencil',
  selectedColor: RELAY_COLORS[1],
  strokeWidth: DEFAULT_STROKE_WIDTH,
  roundLines: DEFAULT_ROUND_LINES,

  setActiveRoundKey: (roundKey) => {
    set({ activeRoundKey: roundKey })
  },

  completeRound: () => {
    const { activeRoundKey } = get()
    const activeRoundIndex = RELAY_ROUND_ORDER.findIndex(
      (roundKey) => roundKey === activeRoundKey,
    )
    const nextRoundKey = RELAY_ROUND_ORDER[activeRoundIndex + 1]

    if (nextRoundKey) {
      set({ activeRoundKey: nextRoundKey })
      return
    }

    // 마지막 라운드 — roomStatus 전환은 서버 ALL_PARTS_COMPLETED/RESULT_CREATED
    // 이벤트가 결정한다. 여기서는 result 슬라이스의 로컬 마무리 상태만 박는다.
    set({
      completedAt: new Date().toISOString(),
      resultRevealStep: 'final',
    })
  },

  setSelectedToolKey: (toolKey) => set({ selectedToolKey: toolKey }),
  setSelectedColor: (color) => set({ selectedColor: color }),
  setStrokeWidth: (strokeWidth) => set({ strokeWidth }),

  commitLine: (line) => {
    const { activeRoundKey, roundLines } = get()
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: [...roundLines[activeRoundKey], line],
      },
    })
  },

  appendPointToLastLine: (point) => {
    const { activeRoundKey, roundLines } = get()
    const currentLines = roundLines[activeRoundKey]
    const latestLine = currentLines[currentLines.length - 1]
    if (!latestLine) return

    const updatedLine: RelayDrawLine = {
      ...latestLine,
      points: [...latestLine.points, point],
    }

    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: [...currentLines.slice(0, -1), updatedLine],
      },
    })
  },

  undoLine: () => {
    const { activeRoundKey, roundLines } = get()
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: roundLines[activeRoundKey].slice(0, -1),
      },
    })
  },

  clearRoundLines: () => {
    const { activeRoundKey, roundLines } = get()
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: [],
      },
    })
  },
})
