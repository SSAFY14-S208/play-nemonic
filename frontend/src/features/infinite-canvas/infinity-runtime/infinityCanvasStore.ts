import { create } from 'zustand'

import {
  INFINITY_ANIMALS,
  INFINITY_COLORS,
  type InfinityAnimal,
  type InfinityPhase,
} from './constants'

interface Participant {
  animal: InfinityAnimal
  color: string
}

interface SessionStats {
  durationSec: number
  strokeCount: number
  colorCount: number
}

interface InfinityCanvasStore {
  phase: InfinityPhase
  myAnimal: InfinityAnimal
  myColor: string
  participants: Participant[]
  sessionStats: SessionStats
  resultImageUrl: string | null

  setPhase: (phase: InfinityPhase) => void
  setMyAnimal: (animal: InfinityAnimal) => void
  setMyColor: (color: string) => void
  setResultImageUrl: (url: string) => void
  incrementStroke: () => void
  resetSession: () => void
}

const MOCK_PARTICIPANTS: Participant[] = [
  { animal: INFINITY_ANIMALS[0], color: '#f4a7b9' },
  { animal: INFINITY_ANIMALS[1], color: '#f9c89b' },
  { animal: INFINITY_ANIMALS[3], color: '#9dd6b0' },
]

export const useInfinityCanvasStore = create<InfinityCanvasStore>((set) => ({
  phase: 'lobby',
  myAnimal: INFINITY_ANIMALS[0],
  myColor: INFINITY_COLORS[4],
  participants: MOCK_PARTICIPANTS,
  sessionStats: { durationSec: 0, strokeCount: 0, colorCount: 0 },
  resultImageUrl: null,

  setPhase: (phase) => set({ phase }),
  setMyAnimal: (myAnimal) => set({ myAnimal }),
  setMyColor: (myColor) => set({ myColor }),
  setResultImageUrl: (resultImageUrl) => set({ resultImageUrl }),
  incrementStroke: () =>
    set((state) => ({
      sessionStats: {
        ...state.sessionStats,
        strokeCount: state.sessionStats.strokeCount + 1,
      },
    })),
  resetSession: () =>
    set({
      phase: 'lobby',
      myAnimal: INFINITY_ANIMALS[0],
      myColor: INFINITY_COLORS[4],
      sessionStats: { durationSec: 0, strokeCount: 0, colorCount: 0 },
      resultImageUrl: null,
    }),
}))
