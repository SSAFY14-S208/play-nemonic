'use client'

import { FLIPBOOK_STEPS } from '../constants'
import type { FlipbookStep } from '../types'
import { cn } from '@/shared/libs'

interface FlipbookStepTabsProps {
  currentStep: FlipbookStep
  onSelectStep: (step: FlipbookStep) => void
}

export default function FlipbookStepTabs({
  currentStep,
  onSelectStep,
}: FlipbookStepTabsProps) {
  return (
    <nav className="fixed bottom-5 left-1/2 z-20 flex -translate-x-1/2 gap-2 rounded-full border border-flipbook-light bg-flipbook-paper/90 p-2 shadow-[0_8px_24px_var(--color-flipbook-shadow)] backdrop-blur">
      {FLIPBOOK_STEPS.map((step) => (
        <button
          key={step.key}
          type="button"
          onClick={() => onSelectStep(step.key)}
          className={cn(
            'caption-b min-h-9 rounded-full px-4 text-flipbook-muted',
            currentStep === step.key && 'bg-flipbook-primary text-flipbook-ink',
          )}
        >
          {step.label}
        </button>
      ))}
    </nav>
  )
}
