'use client'

import { useCallback } from 'react'
import { useParams, useRouter } from 'next/navigation'

import { DEFAULT_USER_NICKNAME } from '@/shared/constants'
import { useUserStore } from '@/shared/stores'

import {
  RelayDismissalModal,
  RelayDrawingView,
  RelayFinalizingView,
  RelayLobbyView,
  RelayNicknameModal,
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
//
// 직접 링크 진입 시 닉네임 게이트 — 부스(`RelayBoothView`)는 needsNicknameSetup
// 검사 후 `RelayNicknameModal`을 먼저 띄우지만, 직접 링크는 부스를 건너뛰므로
// 동일한 게이트를 페이지 진입 시점에 한 번 더 둔다. 게이트 통과 전엔
// useRelayRoom을 호출하지 않아 REST/WS가 닉네임 없이 발사되는 것을 차단한다.
export default function RelayRoomPage() {
  const nickname = useUserStore((state) => state.nickname)
  const needsNicknameSetup =
    !nickname || nickname.trim() === '' || nickname === DEFAULT_USER_NICKNAME

  if (needsNicknameSetup) return <NicknameGate />

  return <RelayRoomPageInner />
}

// 닉네임 미설정 사용자가 직접 링크로 진입했을 때 띄우는 게이트 화면.
// 모달은 강제 노출(open=true). 사용자가 닉네임 입력에 성공하면 store가 갱신돼
// 부모(`RelayRoomPage`)가 재렌더되며 자연스럽게 `RelayRoomPageInner`로 전환된다.
// 사용자가 입력 없이 모달을 닫으면 부스로 push back.
function NicknameGate() {
  const router = useRouter()

  const handleOpenChange = useCallback(
    (open: boolean) => {
      if (open) return
      // 모달이 닫혔을 때 store의 현재 닉네임을 직접 확인한다.
      // 성공 케이스: store가 이미 갱신돼서 부모 재렌더로 게이트가 사라짐.
      // 취소 케이스: 닉네임이 여전히 비어있음 → 부스로 push back.
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
    <section className="relative isolate min-h-screen border border-relay-border bg-relay-background">
      <RelayNicknameModal open onOpenChange={handleOpenChange} />
    </section>
  )
}

function RelayRoomPageInner() {
  const router = useRouter()
  const { roomCode } = useParams<{ roomCode: string }>()
  const { isHydrating, hydrationError } = useRelayRoom(roomCode ?? null)
  const roomStatus = useRelayDrawingStore((state) => state.roomStatus)
  const dismissalReason = useRelayDrawingStore((state) => state.dismissalReason)
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom)

  const handleDismissalConfirm = useCallback(() => {
    clearRoom()
    router.push('/relay-drawing')
  }, [clearRoom, router])

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
            className="body-b min-h-11 cursor-pointer rounded-[var(--radius-md)] bg-relay-accent px-5 text-relay-ink transition-all hover:brightness-105 disabled:hover:brightness-100"
          >
            돌아가기
          </button>
        </div>
      </section>
    )
  }

  const view =
    roomStatus === 'PLAYING' ? (
      <RelayDrawingView />
    ) : roomStatus === 'FINALIZING' ? (
      <RelayFinalizingView />
    ) : roomStatus === 'FINISHED' ? (
      <RelayResultView />
    ) : (
      <RelayLobbyView />
    )

  return (
    <>
      {view}
      {dismissalReason && (
        <RelayDismissalModal
          reason={dismissalReason}
          onConfirm={handleDismissalConfirm}
        />
      )}
    </>
  )
}
