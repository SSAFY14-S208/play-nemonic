import type { StateCreator } from 'zustand'

import { PART_TO_ROUND_KEY, RELAY_ROUND_ORDER } from '../constants'
import {
  appendLineToRound,
  appendPointToLatestLine,
  clearRoundDrawing,
  moveLatestLineToRedoStack,
  restoreLatestRedoLine,
} from './canvasLine.helpers'
import {
  createAssignmentDrawingState,
  createClearedAssignmentState,
  createInitialCanvasState,
  getUpdatedRecentColors,
} from './canvasSlice.helpers'
import type { CanvasSlice, RelayDrawingStore } from './store.types'

export const createCanvasSlice: StateCreator<
  RelayDrawingStore,
  [],
  [],
  CanvasSlice
> = (set, get) => ({
  ...createInitialCanvasState(),

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

    set({ completedAt: new Date().toISOString() })
  },

  setSelectedToolKey: (toolKey) => set({ selectedToolKey: toolKey }),
  setSelectedColor: (color) => set({ selectedColor: color }),
  setSelectedOpacity: (opacity) => set({ selectedOpacity: opacity }),
  setStrokeWidth: (strokeWidth) => set({ strokeWidth }),

  addRecentColor: (color) => {
    set((state) => ({
      recentColors: getUpdatedRecentColors(state.recentColors, color),
    }))
  },

  commitLine: (line) => {
    const { activeRoundKey, roundLines, roundRedoStack } = get()

    set({
      roundLines: appendLineToRound(roundLines, activeRoundKey, line),
      roundRedoStack: {
        ...roundRedoStack,
        [activeRoundKey]: [],
      },
    })
  },

  appendPointToLastLine: (point) => {
    const { activeRoundKey, roundLines } = get()
    const nextRoundLines = appendPointToLatestLine(
      roundLines,
      activeRoundKey,
      point,
    )
    if (!nextRoundLines) return

    set({ roundLines: nextRoundLines })
  },

  undoLine: () => {
    const { activeRoundKey, roundLines, roundRedoStack } = get()
    const nextDrawingState = moveLatestLineToRedoStack(
      roundLines,
      roundRedoStack,
      activeRoundKey,
    )
    if (!nextDrawingState) return

    set(nextDrawingState)
  },

  redoLine: () => {
    const { activeRoundKey, roundLines, roundRedoStack } = get()
    const nextDrawingState = restoreLatestRedoLine(
      roundLines,
      roundRedoStack,
      activeRoundKey,
    )
    if (!nextDrawingState) return

    set(nextDrawingState)
  },

  clearRoundLines: () => {
    const { activeRoundKey, roundLines, roundRedoStack } = get()
    set(clearRoundDrawing(roundLines, roundRedoStack, activeRoundKey))
  },

  setAssignment: (assignment) => {
    const roundKey = PART_TO_ROUND_KEY[assignment.part]

    set({
      ...createAssignmentDrawingState(assignment),
      roundDeadlines: {
        ...get().roundDeadlines,
        [roundKey]: assignment.partDeadlineAt,
      },
    })
  },

  setPartDeadlineAt: (deadline) => set({ partDeadlineAt: deadline }),

  incrementPartFetchTrigger: () =>
    set((state) => ({ partFetchTrigger: state.partFetchTrigger + 1 })),

  setPartTimeUp: (value) => set({ isPartTimeUp: value }),

  triggerPendingAutoSubmit: () =>
    set((state) => ({
      pendingAutoSubmitTrigger: state.pendingAutoSubmitTrigger + 1,
    })),

  setIsSubmitting: (isSubmitting) => set({ isSubmitting }),

  markSubmitted: (roundKey) => {
    const targetRound = roundKey ?? get().activeRoundKey
    const isSameRound = targetRound === get().activeRoundKey

    set({
      isSubmitting: isSameRound ? false : get().isSubmitting,
      isSubmitted: isSameRound ? true : get().isSubmitted,
      roundSubmitted: { ...get().roundSubmitted, [targetRound]: true },
    })
  },

  setRoundDeadline: (roundKey, deadline) =>
    set((state) => ({
      roundDeadlines: { ...state.roundDeadlines, [roundKey]: deadline },
    })),

  updateSubmissionProgress: (submittedCount, totalCount) =>
    set({ submittedCount, totalCount }),

  addSubmittedUserUuid: (userUuid) => {
    const current = get().submittedUserUuids
    if (current.includes(userUuid)) return

    set({ submittedUserUuids: [...current, userUuid] })
  },

  clearSubmittedUserUuids: () => set({ submittedUserUuids: [] }),

  beginTransition: () => set({ isTransitioning: true }),

  advanceToNextRound: () => {
    set({ isTransitioning: false })
  },

  clearAssignment: () => set(createClearedAssignmentState()),
})
