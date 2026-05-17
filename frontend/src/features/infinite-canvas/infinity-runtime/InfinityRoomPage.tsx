'use client'

import { useCallback, useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import { Button } from '@/shared/components'
import { DEFAULT_USER_NICKNAME } from '@/shared/constants'
import { useUserStore } from '@/shared/stores'
import { useInfinityCanvasRoom } from './hooks'
import { InfinityStageView } from './components/InfinityStageView'
import { InfinityNicknameModal } from './components/InfinityNicknameModal'

const MIN_ROOM_LOADING_MS = 700

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
  const [isLoadingSettled, setIsLoadingSettled] = useState(false)

  useEffect(() => {
    if (room.isHydrating) {
      const timer = window.setTimeout(() => {
        setIsLoadingSettled(false)
      }, 0)

      return () => window.clearTimeout(timer)
    }

    const timer = window.setTimeout(() => {
      setIsLoadingSettled(true)
    }, MIN_ROOM_LOADING_MS)

    return () => window.clearTimeout(timer)
  }, [room.isHydrating])

  if (room.isHydrating || !isLoadingSettled) {
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
    <section className="grid min-h-screen place-items-center bg-[radial-gradient(circle_at_50%_42%,rgba(173,213,255,0.42),transparent_28rem),#f9fcff] text-canvas-ink">
      <div className="flex flex-col items-center gap-4 rounded-[32px] border border-white/82 bg-white/78 px-8 py-6 shadow-[0_18px_38px_rgba(71,105,190,0.18),inset_0_1px_0_rgba(255,255,255,0.95)] backdrop-blur-md">
        <div className="flex gap-2" aria-hidden>
          <span className="size-3 rounded-full bg-[#5dc7f2] animate-pulse" />
          <span className="size-3 rounded-full bg-[#8f6dff] animate-pulse [animation-delay:140ms]" />
          <span className="size-3 rounded-full bg-[#ff82c0] animate-pulse [animation-delay:280ms]" />
        </div>
        <p className="body-b text-[#25376c]">무한 캔버스로 이동하고 있어요</p>
      </div>
    </section>
  )
}
