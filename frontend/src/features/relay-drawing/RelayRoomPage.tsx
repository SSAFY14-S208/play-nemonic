'use client'

import { useCallback, useEffect } from 'react'
import Image from 'next/image'
import { useParams, useRouter } from 'next/navigation'
import { AnimatePresence, motion } from 'motion/react'
import { ApiError, postRelayRoomStart } from '@/shared/apis'
import { WorldHomeLink } from '@/shared/components'
import { DEFAULT_USER_NICKNAME } from '@/shared/constants'
import { useUserStore } from '@/shared/stores'

import relayDrawingGameStart from './assets/relay-drawing-game-start.png'
import nemonicDrawingLobbyBg from './assets/nemonic-drawing-lobby-bg.png'
import {
  RelayButton,
  RelayDismissalModal,
  RelayDrawingView,
  RelayFinalizingView,
  RelayLobbyView,
  RelayNicknameModal,
  RelayResultView,
} from './components'
import { useRelayAbandonmentTracking, useRelayRoom } from './hooks'
import { useRelayDrawingStore } from './stores'
import { relayToast } from './utils'
import './relay-drawing.css'

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
    <section className="font-paperlogy relative isolate min-h-screen bg-relay-background">
      <RelayNicknameModal open onOpenChange={handleOpenChange} />
    </section>
  )
}

function RelayRoomPageInner() {
  const router = useRouter()
  const { roomCode } = useParams<{ roomCode: string }>()
  const { isHydrating, hydrationError } = useRelayRoom(roomCode ?? null)
  useRelayAbandonmentTracking()
  const roomStatus = useRelayDrawingStore((state) => state.roomStatus)
  const dismissalReason = useRelayDrawingStore((state) => state.dismissalReason)
  const clearRoom = useRelayDrawingStore((state) => state.clearRoom)
  const gameStartPhase = useRelayDrawingStore((state) => state.gameStartPhase)
  const setGameStartPhase = useRelayDrawingStore(
    (state) => state.setGameStartPhase,
  )
  const hostUserUuid = useRelayDrawingStore((state) => state.hostUserUuid)
  const userUuid = useUserStore((state) => state.userUuid)

  const isHost = userUuid !== null && userUuid === hostUserUuid

  const handleDismissalConfirm = useCallback(() => {
    clearRoom()
    router.push('/relay-drawing')
  }, [clearRoom, router])

  // 게임 시작 이미지 스케일 업 애니메이션 완료 → 호스트만 게임 시작 API 발사.
  // 비호스트는 이미 GAME_STARTED WS 이벤트로 roomStatus가 PLAYING이므로 호출 불필요.
  const handleGameStartImageShown = useCallback(() => {
    if (!isHost || !roomCode) return
    void (async () => {
      try {
        await postRelayRoomStart(roomCode)
      } catch (caughtError) {
        useRelayDrawingStore.getState().setGameStartPhase('idle')
        relayToast.error(
          caughtError instanceof ApiError
            ? caughtError.message
            : '게임 시작에 실패했어요',
        )
      }
    })()
  }, [isHost, roomCode])

  // roomStatus가 PLAYING이 되면 게임 시작 이미지를 잠시 보여준 뒤 drawing으로 전환.
  // 호스트: 이미지 등장 → API → WS(PLAYING) → 1.5s 후 idle → drawing
  // 비호스트: WS(PLAYING + animating 동시) → 1.5s 후 idle → drawing
  useEffect(() => {
    if (roomStatus !== 'PLAYING' || gameStartPhase !== 'animating') return
    const timer = setTimeout(() => {
      setGameStartPhase('idle')
    }, 1500)
    return () => clearTimeout(timer)
  }, [roomStatus, gameStartPhase, setGameStartPhase])

  if (isHydrating) {
    return (
      <section className="font-paperlogy grid min-h-screen place-items-center bg-relay-background text-relay-ink">
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
      <section className="font-paperlogy grid min-h-screen place-items-center bg-relay-background text-relay-ink">
        <div className="flex max-w-sm flex-col items-center gap-4 text-center">
          <p className="body-l-r">{hydrationError}</p>
          <RelayButton onClick={() => router.push('/relay-drawing')}>
            돌아가기
          </RelayButton>
        </div>
      </section>
    )
  }

  // 페이지 도메인 폰트(Paperlogy) 적용 wrap. portal로 분리된 모달은 별도로
  // Dialog.Popup className에 font-paperlogy를 직접 둔다.
  //
  // 배경 이미지는 뷰 전환과 무관하게 상시 마운트. AnimatePresence mode="wait"로
  // 로비 exit 완료 후 다음 뷰가 enter된다 — 로비 좌/우 패널이 양쪽으로 밀려나
  // 배경이 드러난 뒤 드로잉 뷰가 페이드 인하는 연출.
  return (
    <div className="font-paperlogy relative min-h-screen overflow-x-hidden bg-relay-background">
      <Image
        src={nemonicDrawingLobbyBg}
        alt=""
        fill
        priority
        sizes="100vw"
        className="pointer-events-none object-cover"
        aria-hidden
      />
      <div
        className="absolute inset-0 bg-[radial-gradient(circle_at_52%_40%,rgb(255_255_255/36%),transparent_42%)]"
        aria-hidden
      />

      {roomStatus === 'FINISHED' && <WorldHomeLink />}

      <div className="mx-auto w-full max-w-300">
        <AnimatePresence mode="wait">
          {roomStatus === 'PLAYING' && gameStartPhase === 'idle' ? (
            <motion.div
              key="drawing"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.35 }}
            >
              <RelayDrawingView />
            </motion.div>
          ) : roomStatus === 'FINALIZING' ? (
            <motion.div
              key="finalizing"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.3 }}
            >
              <RelayFinalizingView />
            </motion.div>
          ) : roomStatus === 'FINISHED' ? (
            <motion.div
              key="finished"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.3 }}
            >
              <RelayResultView />
            </motion.div>
          ) : (
            <motion.div
              key="lobby"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0, transition: { duration: 0 } }}
              transition={{ duration: 0.55 }}
            >
              <RelayLobbyView />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* 게임 시작 오버레이 — AnimatePresence 밖에 배치해 뷰 전환과 독립적으로
          페이드 아웃된다. 로비 exit(즉시) → 드로잉 enter와 동시에 이미지가 사라지는 연출. */}
      <AnimatePresence>
        {gameStartPhase === 'animating' && (
          <motion.div
            key="game-start-overlay"
            className="fixed inset-0 z-30 grid place-items-center"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
          >
            <motion.div
              initial={{ scale: 0.3, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{
                delay: 0.35,
                duration: 0.5,
                ease: [0.34, 1.56, 0.64, 1],
              }}
              onAnimationComplete={handleGameStartImageShown}
            >
              <Image
                src={relayDrawingGameStart}
                alt="게임 시작!"
                className="h-auto w-[min(90vw,600px)]"
                priority
              />
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {dismissalReason && (
        <RelayDismissalModal
          reason={dismissalReason}
          onConfirm={handleDismissalConfirm}
        />
      )}
    </div>
  )
}
