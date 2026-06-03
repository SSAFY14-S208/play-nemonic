import { useRef } from 'react'

import { cn } from '@/shared/libs'

import ArtworkStack from './ArtworkStack'
import { PART_ASPECT_RATIO, PART_GAP, PART_WIDTH_LG } from './constants'
import { useChoreographyMeasurement, usePartReveal } from './hooks'
import NemoCharacters from './NemoCharacters'
import type { ChoreographyPhase } from './types'

interface ChoreographyTreeProps {
  phase: ChoreographyPhase
  onPhaseChange: (next: ChoreographyPhase) => void
  onComplete: () => void
  onLeftReveal: () => void
  className?: string
}

export default function ChoreographyTree({
  phase,
  onPhaseChange,
  onComplete,
  onLeftReveal,
  className,
}: ChoreographyTreeProps) {
  const slotRef = useRef<HTMLDivElement>(null)
  const measurement = useChoreographyMeasurement(slotRef)
  const handlePartReveal = usePartReveal(phase, onPhaseChange)

  return (
    <>
      <NemoCharacters phase={phase} />
      <div
        ref={slotRef}
        className={cn('relative', className)}
        style={{
          width: measurement?.partWidth ?? PART_WIDTH_LG,
          height: measurement
            ? 3 * measurement.partHeight + 2 * PART_GAP
            : 3 * (PART_WIDTH_LG / PART_ASPECT_RATIO) + 2 * PART_GAP,
        }}
      >
        {measurement !== null && (
          <ArtworkStack
            phase={phase}
            measurement={measurement}
            onPhaseChange={onPhaseChange}
            onComplete={onComplete}
            onLeftReveal={onLeftReveal}
            onPartReveal={handlePartReveal}
          />
        )}
      </div>
    </>
  )
}
