'use client'

import { cn } from '@/shared/libs'

import { PhoneLauncherButton } from '@/shared/components'

import RelayBgmToggle from './RelayBgmToggle'
import RelayHowToPlayButton from './RelayHowToPlayButton'

interface RelayFloatingControlsProps {
  className?: string
  /** 개별 버튼에 전달할 크기 클래스. 기본값 "size-11". */
  buttonClassName?: string
}

// 우상단 floating 컨트롤 (게임 설명 + BGM mute 토글 + phone 열기).
// 로비를 제외한 부스/드로잉/대기/결과 화면에서 공통으로 사용.
// floating PhoneLauncher 숨기기는 페이지 레벨(RelayRoomPageInner)에서 담당한다.
export default function RelayFloatingControls({
  className,
  buttonClassName = 'size-11',
}: RelayFloatingControlsProps) {
  return (
    <div
      className={cn(
        'fixed right-4 top-4 z-[var(--z-sticky)] flex items-center gap-2',
        className,
      )}
    >
      <RelayHowToPlayButton className={buttonClassName} />
      <RelayBgmToggle className={buttonClassName} />
      <PhoneLauncherButton className={buttonClassName} />
    </div>
  )
}
