'use client'

import { HowToPlayModal } from '@/shared/components'

import { RELAY_HOW_TO_PLAY_PANELS } from '../constants'
import { useRelayHowToPlayStore } from '../stores'

// 게임 설명 모달 단일 호스트. relay-drawing 라우트 layout에 1회만 마운트해
// 다중 버튼 인스턴스가 동시에 모달을 띄우는 문제를 막는다.
export default function RelayHowToPlayModalHost() {
  const isOpen = useRelayHowToPlayStore((state) => state.isOpen)
  const setOpen = useRelayHowToPlayStore((state) => state.setOpen)

  return (
    <HowToPlayModal
      open={isOpen}
      onOpenChange={setOpen}
      panels={RELAY_HOW_TO_PLAY_PANELS}
      accentColor="var(--color-relay-accent)"
    />
  )
}
