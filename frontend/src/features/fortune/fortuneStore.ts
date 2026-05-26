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
  setHasUserStoreHydrated: (hasUserStoreHydrated: boolean) => void
  setHasHydrated: (hasHydrated: boolean) => void
  setHasServerBirthInfo: (hasServerBirthInfo: boolean) => void
  setIsSubmittingBirthInfo: (isSubmittingBirthInfo: boolean) => void
  setIsDrawingFortune: (isDrawingFortune: boolean) => void
  setErrorMessage: (errorMessage: string) => void
  resetSession: () => void
}

type FortuneSessionStateValues = Omit<
  FortuneSessionState,
  | 'setStep'
  | 'setBirthInfo'
  | 'setResult'
  | 'setHasUserStoreHydrated'
  | 'setHasHydrated'
  | 'setHasServerBirthInfo'
  | 'setIsSubmittingBirthInfo'
  | 'setIsDrawingFortune'
  | 'setErrorMessage'
  | 'resetSession'
>

const INITIAL_STATE: FortuneSessionStateValues = {
  step: 'intro',
  birthInfo: FORTUNE_EMPTY_BIRTH_INFO,
  result: null,
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
  setHasUserStoreHydrated: (hasUserStoreHydrated) => set({ hasUserStoreHydrated }),
  setHasHydrated: (hasHydrated) => set({ hasHydrated }),
  setHasServerBirthInfo: (hasServerBirthInfo) => set({ hasServerBirthInfo }),
  setIsSubmittingBirthInfo: (isSubmittingBirthInfo) => set({ isSubmittingBirthInfo }),
  setIsDrawingFortune: (isDrawingFortune) => set({ isDrawingFortune }),
  setErrorMessage: (errorMessage) => set({ errorMessage }),
  resetSession: () => set(INITIAL_STATE),
}))
