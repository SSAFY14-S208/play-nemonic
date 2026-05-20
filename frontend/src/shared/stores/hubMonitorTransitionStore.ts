import { create } from 'zustand'
import { HUB_GAMES } from '@/shared/constants'
import { trackHubStoreUpdate } from '@/shared/utils'

interface HubMonitorTransitionRequest {
  gameIndex: number
  requestId: number
}

interface HubMonitorTransitionStore {
  currentRequest: HubMonitorTransitionRequest | null
  lastRequestId: number
  clearRequest: (requestId: number) => void
  requestGameTransition: (gameIndex: number) => void
}

function normalizeGameIndex(gameIndex: number) {
  const gameCount = HUB_GAMES.length

  return ((gameIndex % gameCount) + gameCount) % gameCount
}

export const useHubMonitorTransitionStore =
  create<HubMonitorTransitionStore>((set) => ({
    currentRequest: null,
    lastRequestId: 0,
    clearRequest: (requestId) => {
      set((state) => {
        if (state.currentRequest?.requestId !== requestId) return state

        return { currentRequest: null }
      })
    },
    requestGameTransition: (gameIndex) => {
      set((state) => {
        const requestId = state.lastRequestId + 1

        trackHubStoreUpdate(
          'hubMonitorTransitionStore',
          'requestGameTransition',
        )

        return {
          currentRequest: {
            gameIndex: normalizeGameIndex(gameIndex),
            requestId,
          },
          lastRequestId: requestId,
        }
      })
    },
  }))
