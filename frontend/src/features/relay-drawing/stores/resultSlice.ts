import type { StateCreator } from 'zustand'

import type { RelayDrawingStore, ResultSlice } from './store.types'

export const createResultSlice: StateCreator<RelayDrawingStore, [], [], ResultSlice> = (
  set,
) => ({
  completedAt: null,
  resultItems: [],
  activeResultIndex: 0,

  setResults: (items) => set({ resultItems: items, activeResultIndex: 0 }),

  setActiveResultIndex: (index) => set({ activeResultIndex: index }),

  // 새 게임 시작용 — 캔버스 라인/현재 라운드까지 함께 비운다.
  // (clearRoom과 달리 룸 식별/참여자는 유지)
  resetSession: () => {
    set({
      activeRoundKey: 'face',
      roundLines: { face: [], body: [], legs: [] },
      completedAt: null,
      resultItems: [],
      activeResultIndex: 0,
    })
  },
})
