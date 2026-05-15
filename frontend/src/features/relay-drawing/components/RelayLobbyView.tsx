'use client'

import { GameLobbyLayout } from '@/shared/components'
import type { GameLobbyTheme } from '@/shared/components'
import { useUserStore } from '@/shared/stores'

import relayDrawingTitle from '../assets/relay-drawing-title.png'
import { RELAY_HOW_TO_PLAY_PANELS, RELAY_ROOM_CODE } from '../constants'
import { useRelayLobby } from '../hooks'
import { useRelayDrawingStore } from '../stores'

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
      howToPlayPanels={RELAY_HOW_TO_PLAY_PANELS}
      howToPlayAccentColor="var(--color-relay-accent)"
    />
  )
}
