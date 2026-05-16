'use client'

import { useParams, useRouter } from 'next/navigation'
import { Button } from '@/shared/components'
import { useInfinityCanvasRoom } from './hooks'
import { InfinityStageView } from './components/InfinityStageView'

export function InfinityRoomPage() {
  const router = useRouter()
  const { canvasId } = useParams<{ canvasId: string }>()
  const room = useInfinityCanvasRoom(canvasId ?? null)

  if (room.isHydrating) {
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
