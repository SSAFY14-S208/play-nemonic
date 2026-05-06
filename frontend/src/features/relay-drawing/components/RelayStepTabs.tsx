'use client'

import { RELAY_STEPS } from '../constants'
import { useRelayDrawingStore } from '../relayDrawingStore'
import { cn } from '@/shared/libs'

export default function RelayStepTabs() {
  const currentStep = useRelayDrawingStore((state) => state.currentStep)
  const selectStep = useRelayDrawingStore((state) => state.selectStep)

  return (
    <div className="fixed bottom-5 left-1/2 z-[var(--z-sticky)] flex -translate-x-1/2 rounded-full border border-relay-border bg-relay-header/90 p-1 shadow-soft-lg backdrop-blur-md md:hidden">
      {RELAY_STEPS.map((step) => {
        const isActive = step.key === currentStep

        return (
          <button
            key={step.key}
            type="button"
            onClick={() => selectStep(step.key)}
            className={cn(
              'caption-b min-h-10 min-w-16 rounded-full px-3 text-relay-muted transition-colors',
              isActive && 'bg-relay-accent text-relay-ink shadow-sm',
            )}
          >
            {step.label}
          </button>
        )
      })}
    </div>
  )
}
