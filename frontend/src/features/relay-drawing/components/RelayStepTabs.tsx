import { RELAY_STEPS, type RelayDrawingStep } from '../constants'
import { cn } from '@/shared/libs'

interface RelayStepTabsProps {
  currentStep: RelayDrawingStep
  onSelectStep: (step: RelayDrawingStep) => void
}

export default function RelayStepTabs({ currentStep, onSelectStep }: RelayStepTabsProps) {
  return (
    <div className="fixed bottom-5 left-1/2 z-[var(--z-sticky)] flex -translate-x-1/2 rounded-full border border-relay-border bg-relay-header/90 p-1 shadow-soft-lg backdrop-blur-md md:hidden">
      {RELAY_STEPS.map((step) => {
        const isActive = step.key === currentStep

        return (
          <button
            key={step.key}
            type="button"
            onClick={() => onSelectStep(step.key)}
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
