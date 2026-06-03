'use client'

import ChoreographyTree from './ChoreographyTree'
import { useRelayBoothEntrance } from './hooks'
import RelayBoothEntranceFinalState from './RelayBoothEntranceFinalState'

interface RelayBoothEntranceProps {
  onLeftReveal: () => void
  className?: string
}

export default function RelayBoothEntrance({
  onLeftReveal,
  className,
}: RelayBoothEntranceProps) {
  const { phase, setPhase, isFinalState, setCompleted } =
    useRelayBoothEntrance()

  if (isFinalState) {
    return (
      <RelayBoothEntranceFinalState
        onReveal={onLeftReveal}
        className={className}
      />
    )
  }

  return (
    <ChoreographyTree
      phase={phase}
      onPhaseChange={setPhase}
      onComplete={() => setCompleted(true)}
      onLeftReveal={onLeftReveal}
      className={className}
    />
  )
}
