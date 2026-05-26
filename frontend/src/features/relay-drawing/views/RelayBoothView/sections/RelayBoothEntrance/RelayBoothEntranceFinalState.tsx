import { useEffect, useRef } from 'react'

import { cn } from '@/shared/libs'

import RelayArtworkCard from '../../../../components/RelayArtworkCard'

interface RelayBoothEntranceFinalStateProps {
  onReveal: () => void
  className?: string
}

export default function RelayBoothEntranceFinalState({
  onReveal,
  className,
}: RelayBoothEntranceFinalStateProps) {
  const hasRevealedRef = useRef(false)

  useEffect(() => {
    if (hasRevealedRef.current) return
    hasRevealedRef.current = true
    onReveal()
  }, [onReveal])

  return (
    <div className={cn('relative', className)}>
      <div className="absolute inset-0 z-10 origin-bottom translate-x-15 rotate-15 lg:translate-x-30">
        <RelayArtworkCard variant={1} />
      </div>
      <div className="absolute inset-0 z-20 origin-bottom -translate-x-25 rotate-5 lg:-translate-x-50">
        <RelayArtworkCard variant={3} />
      </div>
      <div className="relative z-30">
        <RelayArtworkCard variant={2} />
      </div>
    </div>
  )
}
