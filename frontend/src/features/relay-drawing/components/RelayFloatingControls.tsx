'use client'

import { cn } from '@/shared/libs'

import RelayBgmToggle from './RelayBgmToggle'
import RelayHowToPlayButton from './RelayHowToPlayButton'

interface RelayFloatingControlsProps {
  className?: string
}

// 우상단 floating 컨트롤 (게임 설명 + BGM mute 토글).
// 로비를 제외한 부스/드로잉/대기/결과 화면에서 공통으로 사용.
export default function RelayFloatingControls({
  className,
}: RelayFloatingControlsProps) {
  return (
    <div
      className={cn(
        'fixed right-4 top-4 z-[var(--z-sticky)] flex items-center gap-3',
        className,
      )}
    >
      <RelayHowToPlayButton />
      <RelayBgmToggle />
    </div>
  )
}
