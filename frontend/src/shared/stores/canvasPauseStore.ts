'use client'

import { create } from 'zustand'

interface CanvasPauseStore {
  isPaused: boolean
  setPaused: (paused: boolean) => void
}

export const useCanvasPauseStore = create<CanvasPauseStore>((set) => ({
  isPaused: false,
  setPaused: (isPaused) => set({ isPaused }),
}))
