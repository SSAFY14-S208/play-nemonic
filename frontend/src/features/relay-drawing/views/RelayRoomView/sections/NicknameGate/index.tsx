'use client'

import { useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { DEFAULT_USER_NICKNAME } from '@/shared/constants'
import { useUserStore } from '@/shared/stores'

import { RelayNicknameModal } from '@/features/relay-drawing/components'

export default function NicknameGate() {
  const router = useRouter()

  const handleOpenChange = useCallback(
    (open: boolean) => {
      if (open) return

      const currentNickname = useUserStore.getState().nickname
      const stillNeedsSetup =
        !currentNickname ||
        currentNickname.trim() === '' ||
        currentNickname === DEFAULT_USER_NICKNAME

      if (stillNeedsSetup) router.replace('/relay-drawing')
    },
    [router],
  )

  return (
    <section className="font-paperlogy relative isolate min-h-screen bg-relay-background">
      <RelayNicknameModal open onOpenChange={handleOpenChange} />
    </section>
  )
}
