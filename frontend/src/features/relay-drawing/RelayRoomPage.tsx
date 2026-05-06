'use client'

import { useParams, useRouter } from 'next/navigation'

import {
  RelayDrawingView,
  RelayFinalizingView,
  RelayLobbyView,
  RelayResultView,
} from './components'
import { useRelayRoom } from './hooks'
import { useRelayDrawingStore } from './stores'

// 라우트: /relay-drawing/[roomCode]
//
// 한 룸의 게임 흐름 전체를 담당한다. WebSocket 연결 1개가 lobby ↔ drawing ↔
// result 전환을 모두 관통하므로(가이드 §22) 라우트 단위가 아니라 이 페이지
// 안에서 view를 스왑한다.
//
// 분기 기준은 서버에서 받은 roomStatus다 — 가이드 §25의 "REST = 진실의
// 기준점, WS = 변화" 원칙. 새로고침으로 RelayRoomPage가 다시 마운트돼도
// useRelayRoom이 URL의 roomCode로 REST hydrate를 한 번 돌려 store를 신선화한다.
export default function RelayRoomPage() {
  const router = useRouter()
  const { roomCode } = useParams<{ roomCode: string }>()
  const { isHydrating, hydrationError } = useRelayRoom(roomCode ?? null)
  const roomStatus = useRelayDrawingStore((state) => state.roomStatus)

  if (isHydrating) {
    return (
      <section className="grid min-h-screen place-items-center bg-relay-background text-relay-ink">
        <div className="flex flex-col items-center gap-4">
          <span
            aria-hidden
            className="size-10 animate-spin rounded-full border-4 border-relay-line border-t-relay-accent"
          />
          <p className="body-l-r">방 정보를 불러오는 중…</p>
        </div>
      </section>
    )
  }

  if (hydrationError) {
    return (
      <section className="grid min-h-screen place-items-center bg-relay-background text-relay-ink">
        <div className="flex max-w-sm flex-col items-center gap-4 text-center">
          <p className="body-l-r">{hydrationError}</p>
          <button
            type="button"
            onClick={() => router.push('/relay-drawing')}
            className="body-b min-h-11 rounded-[var(--radius-md)] bg-relay-accent px-5 text-relay-ink"
          >
            돌아가기
          </button>
        </div>
      </section>
    )
  }

  if (roomStatus === 'PLAYING') return <RelayDrawingView />
  if (roomStatus === 'FINALIZING') return <RelayFinalizingView />
  if (roomStatus === 'FINISHED') return <RelayResultView />
  return <RelayLobbyView />
}
