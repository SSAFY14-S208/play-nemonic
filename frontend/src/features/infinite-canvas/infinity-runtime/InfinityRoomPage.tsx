'use client'

import { useCallback } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { Button } from '@/shared/components'
import { DEFAULT_USER_NICKNAME } from '@/shared/constants'
import { useUserStore } from '@/shared/stores'
import { useInfinityCanvasRoom } from './hooks'
import { InfinityStageView } from './components/InfinityStageView'
import { InfinityNicknameModal } from './components/InfinityNicknameModal'

export function InfinityRoomPage() {
  const userUuid = useUserStore((state) => state.userUuid)
  const nickname = useUserStore((state) => state.nickname)
  const needsNicknameSetup =
    userUuid !== null &&
    (!nickname || nickname.trim() === '' || nickname === DEFAULT_USER_NICKNAME)

  if (!userUuid) return <RoomLoadingView />

  if (needsNicknameSetup) return <NicknameGate />

  return <InfinityRoomPageInner />
}

function NicknameGate() {
  const router = useRouter()

  const handleOpenChange = useCallback(
    (open: boolean) => {
      if (open) return

      const currentNickname = useUserStore.getState().nickname
      const stillNeedsSetup =
        !currentNickname ||
        currentNickname.trim() === '' ||
        currentNickname === DEFAULT_USER_NICKNAME
      if (stillNeedsSetup) router.replace('/infinite-canvas')
    },
    [router],
  )

  return (
    <section className="relative isolate min-h-screen bg-canvas-background text-canvas-ink">
      <InfinityNicknameModal open onOpenChange={handleOpenChange} />
    </section>
  )
}

function InfinityRoomPageInner() {
  const router = useRouter()
  const { roomCode } = useParams<{ roomCode: string }>()
  const room = useInfinityCanvasRoom(roomCode ?? null)

  if (room.isHydrating) {
    return <RoomLoadingView />
  }

  if (room.errorMessage && room.elements.length === 0 && room.participants.length === 0) {
    return (
      <section className="grid min-h-screen place-items-center bg-canvas-background px-6 text-canvas-ink">
        <div className="flex max-w-sm flex-col items-center gap-4 text-center">
          <p className="body-l-r">{room.errorMessage}</p>
          <Button type="button" color="blue" onClick={() => router.replace('/infinite-canvas')}>
            돌아가기
          </Button>
        </div>
      </section>
    )
  }

  return <InfinityStageView room={room} />
}

function RoomLoadingView() {
  return (
    <section className="grid min-h-screen place-items-center bg-canvas-background text-canvas-ink">
      <div className="flex flex-col items-center gap-4">
        <span
          aria-hidden
          className="size-10 animate-spin rounded-full border-4 border-canvas-border border-t-canvas-accent"
        />
        <p className="body-l-r">캔버스를 불러오는 중…</p>
      </div>
    </section>
  )
}
