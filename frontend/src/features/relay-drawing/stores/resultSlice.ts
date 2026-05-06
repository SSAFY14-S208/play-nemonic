import type { StateCreator } from 'zustand'

import { RELAY_RESULT_REVEALS } from '../constants'

import type { RelayDrawingStore, ResultSlice } from './store.types'

export const createResultSlice: StateCreator<RelayDrawingStore, [], [], ResultSlice> = (
  set,
  get,
) => ({
  resultRevealStep: 'final',
  completedAt: null,

  goToNextResultReveal: () => {
    const { resultRevealStep } = get()
    const currentIndex = RELAY_RESULT_REVEALS.findIndex(
      (reveal) => reveal.key === resultRevealStep,
    )
    const nextReveal =
      RELAY_RESULT_REVEALS[Math.min(currentIndex + 1, RELAY_RESULT_REVEALS.length - 1)]
    set({ resultRevealStep: nextReveal.key })
  },

  goToPreviousResultReveal: () => {
    const { resultRevealStep } = get()
    const currentIndex = RELAY_RESULT_REVEALS.findIndex(
      (reveal) => reveal.key === resultRevealStep,
    )
    const previousReveal = RELAY_RESULT_REVEALS[Math.max(currentIndex - 1, 0)]
    set({ resultRevealStep: previousReveal.key })
  },

  // 새 게임 시작용 — 캔버스 라인/현재 라운드까지 함께 비운다.
  // (clearRoom과 달리 룸 식별/참여자는 유지)
  resetSession: () => {
    set({
      activeRoundKey: 'face',
      roundLines: { face: [], body: [], legs: [] },
      completedAt: null,
      resultRevealStep: 'final',
    })
  },
})
