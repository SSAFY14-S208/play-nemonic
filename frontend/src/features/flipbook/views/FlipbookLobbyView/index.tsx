'use client'

import { useMemo, useState } from 'react'
import Image from 'next/image'

import { GameLobbyLayout, HowToPlayModal, PhoneLauncherButton, type GameLobbyTheme, type LobbyParticipant } from '@/shared/components'
import type { FlipbookConnectionStatus } from '@/shared/types'

import { FLIPBOOK_HOW_TO_PLAY_PANELS, FLIPBOOK_SOUND_PATHS } from '../../constants'
import { useFlipbookBgm } from '../../hooks'
import type { FlipbookParticipant, FlipbookTimeLimitSeconds } from '../../types'

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

const FLIPBOOK_LOBBY_CONTROL_IMAGES = {
  howToPlay: '/images/flipbook-entrance-scene/how-to-play-button.png',
  soundOn: '/images/flipbook-entrance-scene/sound-on-button.png',
  soundMuted: '/images/flipbook-entrance-scene/sound-muted-button.png',
} as const

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
  const [isHowToPlayModalOpen, setIsHowToPlayModalOpen] = useState(true)
  const { audioRef, isBgmMuted, toggleFlipbookBgmMuted } = useFlipbookBgm({
    shouldStart: true,
  })
  const lobbyParticipants: LobbyParticipant[] = useMemo(
    () =>
      participants.map((participant) => ({
        userUuid: participant.userUuid,
        nickname: participant.name,
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

  const headerRightSlot = (
    <>
      <FlipbookLobbyIconButton
        imageSrc={FLIPBOOK_LOBBY_CONTROL_IMAGES.howToPlay}
        imageWidth={63}
        imageHeight={70}
        label="게임 설명"
        onClick={() => setIsHowToPlayModalOpen(true)}
      />
      <FlipbookLobbyIconButton
        imageSrc={
          isBgmMuted
            ? FLIPBOOK_LOBBY_CONTROL_IMAGES.soundMuted
            : FLIPBOOK_LOBBY_CONTROL_IMAGES.soundOn
        }
        imageWidth={67}
        imageHeight={70}
        label={isBgmMuted ? '배경음악 켜기' : '배경음악 음소거'}
        pressed={isBgmMuted}
        onClick={toggleFlipbookBgmMuted}
      />
      <PhoneLauncherButton className="size-9 sm:size-10" />
    </>
  )

  return (
    <>
      <audio ref={audioRef} src={FLIPBOOK_SOUND_PATHS.entranceBgm} preload="auto" loop aria-hidden />

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
        headerRightSlot={headerRightSlot}
      />

      <HowToPlayModal
        open={isHowToPlayModalOpen}
        onOpenChange={setIsHowToPlayModalOpen}
        panels={FLIPBOOK_HOW_TO_PLAY_PANELS}
        title="플립북 게임 설명"
        subtitle="이전 프레임을 힌트로 보며 조금씩 바꿔 그려 움직이는 플립북을 만들어요."
        accentColor="#ff7182"
      />

      {errorMessage && (
        <div className="fixed inset-x-0 bottom-[calc(1rem+env(safe-area-inset-bottom))] z-50 flex justify-center px-4">
          <p
            className="body-b max-w-[calc(100vw-2rem)] break-words rounded-xl px-5 py-3 shadow-lg"
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

function FlipbookLobbyIconButton({
  imageSrc,
  imageWidth,
  imageHeight,
  label,
  pressed,
  onClick,
}: {
  imageSrc: string
  imageWidth: number
  imageHeight: number
  label: string
  pressed?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      title={label}
      className="relative grid size-9 place-items-center transition duration-150 hover:-translate-y-0.5 active:translate-y-px active:scale-95 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-flipbook-primary sm:size-10"
      onClick={onClick}
    >
      <Image
        src={imageSrc}
        alt=""
        width={imageWidth}
        height={imageHeight}
        sizes="70px"
        className="h-full w-auto object-contain"
      />
      <span className="sr-only">{label}</span>
    </button>
  )
}
