'use client'

import { useMemo } from 'react'

import { GameLobbyLayout } from '@/shared/components'
import type { GameLobbyTheme, LobbyParticipant } from '@/shared/components'
import type { FlipbookConnectionStatus } from '@/shared/types'

import { FLIPBOOK_HOW_TO_PLAY_PANELS } from '../constants'
import type { FlipbookParticipant, FlipbookTimeLimitSeconds } from '../types'

const FLIPBOOK_TITLE_IMAGE = '/images/flipbook-entrance-scene/title-logo-sprite.png'
const FLIPBOOK_LOBBY_BACKGROUND = '/images/flipbook-lobby/background.png'
const FLIPBOOK_LOBBY_BACKGROUND_OVERLAY =
  'radial-gradient(circle at 52% 40%, rgb(255 255 255 / 36%), transparent 42%)'

const FLIPBOOK_LOBBY_THEME: GameLobbyTheme = {
  accent: '#ff7182',
  accentStrong: '#ff7182',
  ink: '#684834',
  paper: '#fffaf3',
  paperAlpha: 'rgba(255, 250, 243, 0.82)',
  active: '#fff2e9',
  line: '#efd8c7',
  muted: '#9a7f6d',
  dash: '#b49d91',
  qrDark: '#684834',
  qrLight: '#fffaf3',
}

interface FlipbookLobbyViewProps {
  currentParticipant: FlipbookParticipant
  participants: FlipbookParticipant[]
  roomCode: string | null
  minParticipants: number
  maxParticipants: number
  selectedTimeLimitSeconds: number
  timeLimitOptions: FlipbookTimeLimitSeconds[]
  connectionStatus: FlipbookConnectionStatus
  canStartGame: boolean
  isHost: boolean
  isBusy: boolean
  errorMessage: string | null
  onSelectTimeLimit: (seconds: FlipbookTimeLimitSeconds) => void
  onStartGame: () => void
  onLeaveRoom: () => void
  onKickParticipant: (targetUserUuid: string) => void
}

export default function FlipbookLobbyView({
  currentParticipant,
  participants,
  roomCode,
  minParticipants,
  maxParticipants,
  selectedTimeLimitSeconds,
  timeLimitOptions,
  connectionStatus,
  canStartGame,
  isHost,
  isBusy,
  errorMessage,
  onSelectTimeLimit,
  onStartGame,
  onLeaveRoom,
  onKickParticipant,
}: FlipbookLobbyViewProps) {
  const lobbyParticipants: LobbyParticipant[] = useMemo(
    () =>
      participants.map((participant) => ({
        userUuid: participant.userUuid,
        nickname: participant.name.replace(/ \(나\)$/, ''),
        host: participant.isHost === true,
        connected: participant.isConnected === true,
      })),
    [participants],
  )

  const isConnectionReady = connectionStatus === 'connected'
  const startButtonLabel = !isHost
    ? '게임 대기중'
    : isBusy
      ? '시작 중…'
      : `게임 시작 (${participants.length}명)`

  return (
    <>
      <GameLobbyLayout
        theme={FLIPBOOK_LOBBY_THEME}
        titleImage={FLIPBOOK_TITLE_IMAGE}
        titleImageAlt="플립북"
        subtitle="친구들이 모이면 바로 시작해요!"
        roomCode={roomCode ?? '------'}
        participants={lobbyParticipants}
        maxParticipants={maxParticipants}
        minParticipants={minParticipants}
        currentUserUuid={currentParticipant.userUuid}
        participantListMaxHeight={220}
        isHost={isHost}
        onKickParticipant={onKickParticipant}
        timeLimitSeconds={selectedTimeLimitSeconds}
        timeLimitAllowedSeconds={timeLimitOptions}
        onChangeTimeLimit={onSelectTimeLimit}
        canStartGame={canStartGame && isConnectionReady}
        isStarting={isBusy}
        startButtonLabel={startButtonLabel}
        onStartGame={onStartGame}
        onLeave={onLeaveRoom}
        backgroundImage={FLIPBOOK_LOBBY_BACKGROUND}
        backgroundOverlay={FLIPBOOK_LOBBY_BACKGROUND_OVERLAY}
        howToPlayPanels={FLIPBOOK_HOW_TO_PLAY_PANELS}
        howToPlayAccentColor="#ff7182"
      />

      {errorMessage && (
        <div className="fixed inset-x-0 bottom-6 z-50 flex justify-center px-4">
          <p
            className="body-b rounded-xl px-5 py-3 shadow-lg"
            role="alert"
            style={{
              backgroundColor: '#fff0f1',
              color: '#c0392b',
              border: '1px solid #f5c6cb',
            }}
          >
            {errorMessage}
          </p>
        </div>
      )}
    </>
  )
}
