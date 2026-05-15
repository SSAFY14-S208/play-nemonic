import { create } from 'zustand'
import { HUB_GAMES } from '@/shared/constants'
import { trackHubStoreUpdate } from '@/shared/utils'

interface HubGameStore {
  selectedGameIndex: number
  selectGame: (gameIndex: number) => void
  selectNextGame: () => void
  selectPreviousGame: () => void
}

function normalizeGameIndex(gameIndex: number) {
  const gameCount = HUB_GAMES.length

  return ((gameIndex % gameCount) + gameCount) % gameCount
}

export const useHubGameStore = create<HubGameStore>((set) => ({
  selectedGameIndex: 0,
  selectGame: (gameIndex) => {
    trackHubStoreUpdate('hubGameStore', 'selectGame')
    set({ selectedGameIndex: normalizeGameIndex(gameIndex) })
  },
  selectNextGame: () => {
    trackHubStoreUpdate('hubGameStore', 'selectNextGame')
    set((state) => ({
      selectedGameIndex: normalizeGameIndex(state.selectedGameIndex + 1),
    }))
  },
  selectPreviousGame: () => {
    trackHubStoreUpdate('hubGameStore', 'selectPreviousGame')
    set((state) => ({
      selectedGameIndex: normalizeGameIndex(state.selectedGameIndex - 1),
    }))
  },
}))
