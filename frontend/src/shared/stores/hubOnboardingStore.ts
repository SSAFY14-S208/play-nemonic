'use client'

import { create } from 'zustand'

const HUB_ONBOARDING_SEEN_STORAGE_KEY = 'play-nemonic:hub-onboarding-seen'

function readHasSeenOnboarding() {
  if (typeof window === 'undefined') return false

  try {
    return window.localStorage.getItem(HUB_ONBOARDING_SEEN_STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

function persistHasSeenOnboarding() {
  try {
    window.localStorage.setItem(HUB_ONBOARDING_SEEN_STORAGE_KEY, 'true')
  } catch {
    // Storage may be unavailable in restricted browser modes.
  }
}

interface HubOnboardingStore {
  currentStepIndex: number
  hasEnteredHub: boolean
  hasHydratedFromStorage: boolean
  hasSeenOnboarding: boolean
  setCurrentStepIndex: (stepIndex: number) => void
  setHasEnteredHub: (hasEntered: boolean) => void
  dismissOnboarding: () => void
  reopenOnboarding: () => void
  hydrateFromStorage: () => void
}

export const useHubOnboardingStore = create<HubOnboardingStore>((set) => ({
  currentStepIndex: 0,
  hasEnteredHub: false,
  hasHydratedFromStorage: false,
  hasSeenOnboarding: false,
  setCurrentStepIndex: (currentStepIndex) => set({ currentStepIndex }),
  setHasEnteredHub: (hasEnteredHub) => set({ hasEnteredHub }),
  dismissOnboarding: () => {
    persistHasSeenOnboarding()
    set({ currentStepIndex: 0, hasSeenOnboarding: true })
  },
  reopenOnboarding: () =>
    set({ currentStepIndex: 0, hasSeenOnboarding: false }),
  hydrateFromStorage: () =>
    set({
      hasHydratedFromStorage: true,
      hasSeenOnboarding: readHasSeenOnboarding(),
    }),
}))
