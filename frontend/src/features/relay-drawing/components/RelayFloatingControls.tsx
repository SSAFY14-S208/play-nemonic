'use client'

import { useEffect } from 'react'

import { cn } from '@/shared/libs'
import { usePhoneLauncherStore } from '@/shared/stores'

import { PhoneLauncherButton } from '@/shared/components'

import RelayBgmToggle from './RelayBgmToggle'
import RelayHowToPlayButton from './RelayHowToPlayButton'

interface RelayFloatingControlsProps {
  className?: string
}

// 우상단 floating 컨트롤 (게임 설명 + BGM mute 토글 + phone 열기).
// 로비를 제외한 부스/드로잉/대기/결과 화면에서 공통으로 사용.
// 마운트 중에는 floating PhoneLauncher 버튼을 숨기고 인라인 버튼으로 대체한다.
export default function RelayFloatingControls({
  className,
}: RelayFloatingControlsProps) {
  const setLauncherHidden = usePhoneLauncherStore(
    (state) => state.setLauncherHidden,
  )
  useEffect(() => {
    setLauncherHidden(true)
    return () => setLauncherHidden(false)
  }, [setLauncherHidden])

  return (
    <div
      className={cn(
        'fixed right-4 top-4 z-[var(--z-sticky)] flex items-center gap-3',
        className,
      )}
    >
      <RelayHowToPlayButton />
      <RelayBgmToggle />
      <PhoneLauncherButton />
    </div>
  )
}
