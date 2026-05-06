import { create } from 'zustand'
import {
  RELAY_COLORS,
  RELAY_RESULT_REVEALS,
  RELAY_ROUND_ORDER,
  RELAY_STEPS,
  type RelayDrawingStep,
  type RelayResultRevealStep,
  type RelayRoundKey,
  type RelayToolKey,
} from './constants'
import type { RelayDrawLine, RelayDrawPoint, RelayRoundLines } from './types'

const DEFAULT_STROKE_WIDTH = 4
const DEFAULT_ROUND_LINES: RelayRoundLines = {
  face: [],
  body: [],
  legs: [],
}

interface RelayDrawingStore {
  currentStep: RelayDrawingStep
  activeRoundKey: RelayRoundKey
  selectedToolKey: RelayToolKey
  selectedColor: string
  strokeWidth: number
  roundLines: RelayRoundLines
  resultRevealStep: RelayResultRevealStep
  completedAt: string | null

  selectStep: (step: RelayDrawingStep) => void
  goToNextStep: () => void
  goToPreviousStep: () => void
  setActiveRoundKey: (roundKey: RelayRoundKey) => void
  completeRound: () => void
  setSelectedToolKey: (toolKey: RelayToolKey) => void
  setSelectedColor: (color: string) => void
  setStrokeWidth: (strokeWidth: number) => void
  commitLine: (line: RelayDrawLine) => void
  appendPointToLastLine: (point: RelayDrawPoint) => void
  undoLine: () => void
  clearRoundLines: () => void
  goToNextResultReveal: () => void
  goToPreviousResultReveal: () => void
  resetSession: () => void
}

export const useRelayDrawingStore = create<RelayDrawingStore>((set, get) => ({
  currentStep: 'booth',
  activeRoundKey: 'face',
  selectedToolKey: 'pencil',
  selectedColor: RELAY_COLORS[1],
  strokeWidth: DEFAULT_STROKE_WIDTH,
  roundLines: DEFAULT_ROUND_LINES,
  resultRevealStep: 'final',
  completedAt: null,

  selectStep: (step) => {
    set({ currentStep: step })
    if (step === 'drawing') {
      set({ activeRoundKey: 'face' })
    }
    if (step === 'result') {
      set({ resultRevealStep: 'final' })
    }
  },

  goToNextStep: () => {
    const { currentStep } = get()
    const currentStepIndex = RELAY_STEPS.findIndex((step) => step.key === currentStep)
    const nextStep = RELAY_STEPS[Math.min(currentStepIndex + 1, RELAY_STEPS.length - 1)]

    set({ currentStep: nextStep.key })

    if (nextStep.key === 'drawing') {
      get().resetSession()
    }
    if (nextStep.key === 'result') {
      set({ resultRevealStep: 'final' })
    }
  },

  goToPreviousStep: () => {
    const { currentStep } = get()
    const currentStepIndex = RELAY_STEPS.findIndex((step) => step.key === currentStep)
    const previousStep = RELAY_STEPS[Math.max(currentStepIndex - 1, 0)]
    set({ currentStep: previousStep.key })
  },

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

    set({
      completedAt: new Date().toISOString(),
      resultRevealStep: 'final',
      currentStep: 'result',
    })
  },

  setSelectedToolKey: (toolKey) => {
    set({ selectedToolKey: toolKey })
  },

  setSelectedColor: (color) => {
    set({ selectedColor: color })
  },

  setStrokeWidth: (strokeWidth) => {
    set({ strokeWidth })
  },

  commitLine: (line) => {
    const { activeRoundKey, roundLines } = get()
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: [...roundLines[activeRoundKey], line],
      },
    })
  },

  appendPointToLastLine: (point) => {
    const { activeRoundKey, roundLines } = get()
    const currentLines = roundLines[activeRoundKey]
    const latestLine = currentLines[currentLines.length - 1]
    if (!latestLine) return

    const updatedLine: RelayDrawLine = {
      ...latestLine,
      points: [...latestLine.points, point],
    }

    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: [...currentLines.slice(0, -1), updatedLine],
      },
    })
  },

  undoLine: () => {
    const { activeRoundKey, roundLines } = get()
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: roundLines[activeRoundKey].slice(0, -1),
      },
    })
  },

  clearRoundLines: () => {
    const { activeRoundKey, roundLines } = get()
    set({
      roundLines: {
        ...roundLines,
        [activeRoundKey]: [],
      },
    })
  },

  goToNextResultReveal: () => {
    const { resultRevealStep } = get()
    const currentIndex = RELAY_RESULT_REVEALS.findIndex(
      (reveal) => reveal.key === resultRevealStep,
    )
    const nextReveal = RELAY_RESULT_REVEALS[
      Math.min(currentIndex + 1, RELAY_RESULT_REVEALS.length - 1)
    ]
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

  resetSession: () => {
    set({
      activeRoundKey: 'face',
      roundLines: { face: [], body: [], legs: [] },
      completedAt: null,
      resultRevealStep: 'final',
    })
  },
}))
