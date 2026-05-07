'use client'

import { create } from 'zustand'

import { FORTUNE_EMPTY_BIRTH_INFO } from './constants'
import type { FortuneBirthInfo, FortuneResult, FortuneStep } from './types'

interface FortuneSessionState {
  step: FortuneStep
  birthInfo: FortuneBirthInfo
  result: FortuneResult | null
  hasUserStoreHydrated: boolean
  hasHydrated: boolean
  hasServerBirthInfo: boolean
  isSubmittingBirthInfo: boolean
  isDrawingFortune: boolean
  errorMessage: string

  setStep: (step: FortuneStep) => void
  setBirthInfo: (birthInfo: FortuneBirthInfo) => void
  setResult: (result: FortuneResult | null) => void
  setUserStoreHydrated: (hasUserStoreHydrated: boolean) => void
  setHydrated: (hasHydrated: boolean) => void
  setHasServerBirthInfo: (hasServerBirthInfo: boolean) => void
  setSubmittingBirthInfo: (isSubmittingBirthInfo: boolean) => void
  setDrawingFortune: (isDrawingFortune: boolean) => void
  setErrorMessage: (errorMessage: string) => void
  resetSession: () => void
}

const INITIAL_STATE = {
  step: 'intro' as FortuneStep,
  birthInfo: FORTUNE_EMPTY_BIRTH_INFO,
  result: null as FortuneResult | null,
  hasUserStoreHydrated: false,
  hasHydrated: false,
  hasServerBirthInfo: false,
  isSubmittingBirthInfo: false,
  isDrawingFortune: false,
  errorMessage: '',
}

export const useFortuneSessionStore = create<FortuneSessionState>((set) => ({
  ...INITIAL_STATE,
  setStep: (step) => set({ step }),
  setBirthInfo: (birthInfo) => set({ birthInfo }),
  setResult: (result) => set({ result }),
  setUserStoreHydrated: (hasUserStoreHydrated) => set({ hasUserStoreHydrated }),
  setHydrated: (hasHydrated) => set({ hasHydrated }),
  setHasServerBirthInfo: (hasServerBirthInfo) => set({ hasServerBirthInfo }),
  setSubmittingBirthInfo: (isSubmittingBirthInfo) => set({ isSubmittingBirthInfo }),
  setDrawingFortune: (isDrawingFortune) => set({ isDrawingFortune }),
  setErrorMessage: (errorMessage) => set({ errorMessage }),
  resetSession: () => set(INITIAL_STATE),
}))
