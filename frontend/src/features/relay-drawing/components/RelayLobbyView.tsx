'use client'

import { useEffect } from 'react'

import { GameLobbyLayout } from '@/shared/components'
import type { GameLobbyTheme } from '@/shared/components'
import { usePhoneLauncherStore, useUserStore } from '@/shared/stores'

import relayDrawingTitle from '../assets/relay-drawing-title.png'
import { RELAY_ROOM_CODE } from '../constants'
import { useRelayLobby } from '../hooks'
import { useRelayDrawingStore, useRelayHowToPlayStore } from '../stores'
import RelayBgmToggle from './RelayBgmToggle'
import RelayHowToPlayButton from './RelayHowToPlayButton'
import { PhoneLauncherButton } from '@/shared/components'

const RELAY_LOBBY_THEME: GameLobbyTheme = {
  accent: 'var(--color-relay-accent)',
  accentStrong: 'var(--color-relay-accent-strong)',
  ink: 'var(--color-relay-ink)',
  paper: 'var(--color-relay-paper)',
  paperAlpha: 'rgba(255, 255, 255, 0.78)',
  active: 'var(--color-relay-active)',
  line: 'var(--color-relay-line)',
  muted: 'var(--color-relay-muted)',
  dash: 'var(--color-relay-dash)',
  qrDark: '#5b3e2b',
  qrLight: '#fffaf3',
}

export default function RelayLobbyView() {
  const roomCode = useRelayDrawingStore((state) => state.roomCode)
  const roomStatus = useRelayDrawingStore((state) => state.roomStatus)
  const participants = useRelayDrawingStore((state) => state.participants)
  const maxParticipants = useRelayDrawingStore(
    (state) => state.maxParticipants,
  )
  const minParticipants = useRelayDrawingStore(
    (state) => state.minParticipants,
  )
  const timeLimitSeconds = useRelayDrawingStore(
    (state) => state.timeLimitSeconds,
  )
  const timeLimitAllowedSeconds = useRelayDrawingStore(
    (state) => state.timeLimitAllowedSeconds,
  )
  const currentUserUuid = useUserStore((state) => state.userUuid)

  const gameStartPhase = useRelayDrawingStore(
    (state) => state.gameStartPhase,
  )
  const isExiting = gameStartPhase === 'animating'

  const {
    isHost,
    canStartGame,
    isStarting,
    settingsError,
    kickingTargetUuid,
    kickError,
    startGame,
    changeTimeLimit,
    kickParticipant,
    leaveRoom,
  } = useRelayLobby()

  // 로비 진입 시 게임 설명 모달을 자동으로 1회 연다. 모달은 layout에 마운트된
  // 단일 호스트가 렌더하므로 여기서는 store만 갱신.
  //
  // roomStatus === 'WAITING' 가드: 결과 화면에서 "로비로" 버튼을 누르면
  // clearRoom()으로 roomStatus가 null이 된 직후 라우터 전환 전 한 프레임 동안
  // 이 컴포넌트가 마운트될 수 있다. 가드 없이 호출하면 booth 페이지로 이동한
  // 뒤에도 모달이 열린 상태로 끌려간다. 실제 로비 상태일 때만 open한다.
  const openHowToPlay = useRelayHowToPlayStore((state) => state.open)
  useEffect(() => {
    if (roomStatus !== 'WAITING') return
    openHowToPlay()
  }, [roomStatus, openHowToPlay])

  // 로비에서는 floating PhoneLauncher 버튼을 숨기고, 헤더 인라인 버튼으로 대체.
  const setLauncherHidden = usePhoneLauncherStore(
    (state) => state.setLauncherHidden,
  )
  useEffect(() => {
    setLauncherHidden(true)
    return () => setLauncherHidden(false)
  }, [setLauncherHidden])

  const startButtonLabel = isStarting
    ? '시작 중…'
    : `게임 시작 (${participants.length}명)`

  return (
    <GameLobbyLayout
      theme={RELAY_LOBBY_THEME}
      titleImage={relayDrawingTitle}
      titleImageAlt="네모닉 드로잉"
      subtitle="친구들이 모이면 바로 시작해요!"
      roomCode={roomCode ?? RELAY_ROOM_CODE}
      participants={participants}
      maxParticipants={maxParticipants}
      minParticipants={minParticipants}
      currentUserUuid={currentUserUuid}
      participantListMaxHeight={320}
      isHost={isHost}
      kickingTargetUuid={kickingTargetUuid}
      kickError={kickError}
      onKickParticipant={kickParticipant}
      timeLimitSeconds={timeLimitSeconds}
      timeLimitAllowedSeconds={timeLimitAllowedSeconds}
      onChangeTimeLimit={changeTimeLimit}
      settingsError={settingsError}
      canStartGame={canStartGame}
      isStarting={isStarting}
      startButtonLabel={startButtonLabel}
      onStartGame={startGame}
      onLeave={leaveRoom}
      isExiting={isExiting}
      headerRightSlot={
        <>
          <RelayHowToPlayButton />
          <RelayBgmToggle />
          <PhoneLauncherButton />
        </>
      }
    />
  )
}
