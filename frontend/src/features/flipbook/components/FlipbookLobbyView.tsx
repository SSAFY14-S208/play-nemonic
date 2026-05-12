'use client'

import Image from 'next/image'
import {
  Clock3,
  Copy,
  Flag,
  Minus,
  Plus,
  QrCode,
  UsersRound,
} from 'lucide-react'
import { cn } from '@/shared/libs'
import type { FlipbookConnectionStatus } from '@/shared/types'
import { FLIPBOOK_TIME_LIMITS_SECONDS } from '../constants'
import type { FlipbookParticipant, FlipbookTimeLimitSeconds } from '../types'
import FlipbookLobbyShareButton from './FlipbookLobbyShareButton'
import {
  FlipbookMobileLobbyLayout,
  ParticipantNameTag,
  WaitingParticipantSlot,
  type FlipbookLobbyShareAction,
} from './lobby-view'

interface FlipbookLobbyViewProps {
  currentParticipant: FlipbookParticipant
  participants: FlipbookParticipant[]
  roomCode: string | null
  participantCount: number
  maxParticipants: number
  selectedTimeLimitSeconds: number
  roundCount: number | null
  connectionStatus: FlipbookConnectionStatus
  canStartGame: boolean
  isHost: boolean
  isBusy: boolean
  errorMessage: string | null
  onSelectTimeLimit: (seconds: FlipbookTimeLimitSeconds) => void
  onSelectRoundCount: (roundCount: number) => void
  onStartGame: () => void
}

const FLIPBOOK_LOBBY_IMAGES = {
  background: '/images/flipbook-lobby/background.png',
  startButton: '/images/flipbook-lobby/start-button.png',
  copyLinkButton: '/images/flipbook-lobby/copy-link-button.png?v=2',
  qrCodeButton: '/images/flipbook-lobby/qr-code-button.png?v=2',
  plus: '/images/flipbook-lobby/plus.svg',
}
const SHARE_ACTIONS: FlipbookLobbyShareAction[] = [
  { key: 'copyLink', label: '링크 복사', Icon: Copy },
  { key: 'qrCode', label: 'QR 코드', Icon: QrCode },
]
const VISIBLE_PARTICIPANT_SLOT_LIMIT = 6
const MINIMUM_FRAME_COUNT_PER_FLIPBOOK = 8

export default function FlipbookLobbyView({
  currentParticipant,
  participants,
  roomCode,
  participantCount,
  maxParticipants,
  selectedTimeLimitSeconds,
  roundCount,
  connectionStatus,
  canStartGame,
  isHost,
  isBusy,
  errorMessage,
  onSelectTimeLimit,
  onSelectRoundCount,
  onStartGame,
}: FlipbookLobbyViewProps) {
  const sessionParticipantName = currentParticipant.name.replace(' (나)', '')
  const displayedParticipants = participants.slice(0, VISIBLE_PARTICIPANT_SLOT_LIMIT)
  const visibleParticipantCount = Math.min(participantCount, maxParticipants)
  const waitingSlots = Array.from(
    {
      length: Math.max(
        0,
        Math.min(maxParticipants, VISIBLE_PARTICIPANT_SLOT_LIMIT) - displayedParticipants.length,
      ),
    },
    (_, waitingSlotIndex) => `waiting-${waitingSlotIndex}`,
  )
  const isConnectionReady = connectionStatus === 'connected'
  const startGameButtonDisabled = !isHost || !canStartGame || !isConnectionReady || isBusy
  const startGameButtonLabel = !isHost ? '게임 대기중' : isBusy ? '시작 중' : '게임 시작!'
  const roundControlDisabled = !isHost || roundCount === null || isBusy
  const minimumRoundCount = Math.max(
    1,
    Math.ceil(MINIMUM_FRAME_COUNT_PER_FLIPBOOK / Math.max(2, participantCount)),
  )
  const canDecreaseRoundCount = !roundControlDisabled && roundCount > minimumRoundCount

  return (
    <section className="relative min-h-screen overflow-y-auto bg-[#fff5ed] text-[#684834] lg:grid lg:h-screen lg:place-items-center lg:overflow-hidden">
      <Image
        src={FLIPBOOK_LOBBY_IMAGES.background}
        alt=""
        fill
        priority
        sizes="100vw"
        className="object-cover"
      />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_52%_40%,rgb(255_255_255_/_36%),transparent_42%)]" />

      <FlipbookMobileLobbyLayout
        currentParticipant={currentParticipant}
        displayedParticipants={displayedParticipants}
        sessionParticipantName={sessionParticipantName}
        waitingSlots={waitingSlots}
        roomCode={roomCode}
        visibleParticipantCount={visibleParticipantCount}
        maxParticipants={maxParticipants}
        selectedTimeLimitSeconds={selectedTimeLimitSeconds}
        roundCount={roundCount}
        minimumRoundCount={minimumRoundCount}
        isHost={isHost}
        canDecreaseRoundCount={canDecreaseRoundCount}
        roundControlDisabled={roundControlDisabled}
        startGameButtonDisabled={startGameButtonDisabled}
        startGameButtonLabel={startGameButtonLabel}
        errorMessage={errorMessage}
        shareActions={SHARE_ACTIONS}
        images={FLIPBOOK_LOBBY_IMAGES}
        onSelectTimeLimit={onSelectTimeLimit}
        onSelectRoundCount={onSelectRoundCount}
        onStartGame={onStartGame}
      />

      <div className="relative z-10 hidden h-[720px] w-[1170px] shrink-0 lg:block">
        <div className="absolute left-0 top-0 grid h-[1050px] w-[1720px] origin-top-left scale-[0.68] px-[80px] py-[44px]">
          <main className="grid items-center gap-[70px] lg:grid-cols-[minmax(390px,0.82fr)_minmax(650px,1.18fr)]">
            <aside className="relative mx-auto flex w-full max-w-[620px] flex-col items-center lg:mx-0">
              <div className="relative w-full max-w-[480px] pb-8 text-center">
                <p
                  className="text-[112px] leading-[0.95] text-[#5b3e2b]"
                  style={{ fontFamily: 'var(--font-paperlogy)' }}
                >
                  플립북
                </p>
                <div className="mx-auto mt-8 inline-flex min-h-[52px] items-center rounded-[10px] bg-[#f8b5ba]/62 px-10 text-[#684834] shadow-[inset_0_-8px_0_rgb(255_255_255_/_24%)]">
                  <span className="body-l-b">친구들이 모이면 바로 시작해요!</span>
                </div>
              </div>

              <section className="relative mt-[42px] w-full max-w-[560px] rounded-[34px] border border-[#e9cdb8] bg-[#fffaf3]/82 px-[52px] pb-[56px] pt-[96px] text-center shadow-[0_16px_34px_rgb(122_72_38_/_14%),inset_0_0_34px_rgb(255_244_226_/_70%)] backdrop-blur-[1px]">
                <p className="h3-b text-[#684834]">입장 코드</p>
              <p
                className="mt-5 break-all text-[clamp(52px,5.5vw,84px)] font-black leading-none text-[#684834]"
                style={{ letterSpacing: '0.04em' }}
              >
                {roomCode ?? '------'}
              </p>
              <div
                aria-hidden
                className="mx-auto mt-6 h-3 w-[min(72%,330px)] rounded-full bg-[repeating-linear-gradient(90deg,#ffd96d_0_16px,transparent_16px_24px)]"
              />
              <p className="body-l-b mt-7 text-[#b19686]">친구에게 코드를 알려주세요!</p>

              <div className="mt-9 grid grid-cols-2 gap-5">
                {SHARE_ACTIONS.map((action) => (
                  <FlipbookLobbyShareButton
                    key={action.key}
                    actionKey={action.key}
                    label={action.label}
                    Icon={action.Icon}
                    roomCode={roomCode}
                    images={FLIPBOOK_LOBBY_IMAGES}
                  />
                ))}
              </div>
              </section>
            </aside>

            <section className="relative rounded-[46px] border border-[#efd8c7] bg-white/68 p-[48px] shadow-[0_18px_44px_rgb(126_74_42_/_14%),inset_0_1px_0_rgb(255_255_255_/_86%)] backdrop-blur-sm">
            <div className="flex items-center justify-between border-b border-[#edd9c9] pb-7">
              <h2 className="h2-b inline-flex items-center gap-4 text-[#684834]">
                <UsersRound className="size-8" strokeWidth={2.2} aria-hidden />
                참여자
              </h2>
              <span className="h1-b text-[#ff7182]">
                {visibleParticipantCount} / {maxParticipants}
              </span>
            </div>

            <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2">
              {displayedParticipants.map((participant) => (
                <ParticipantNameTag
                  key={participant.userUuid}
                  name={
                    participant.userUuid === currentParticipant.userUuid
                      ? sessionParticipantName
                      : participant.name
                  }
                  avatar={participant.avatar}
                  isHost={participant.isHost === true}
                />
              ))}
              {waitingSlots.map((waitingSlot) => (
                <WaitingParticipantSlot key={waitingSlot} plusImageSrc={FLIPBOOK_LOBBY_IMAGES.plus} />
              ))}
            </div>

            <div className="mt-8 grid gap-6 xl:grid-cols-2">
              <section className="rounded-[22px] border border-[#efd8c7] bg-white/54 p-6 shadow-[inset_0_1px_0_rgb(255_255_255_/_82%)]">
                <h3 className="h3-b inline-flex items-center gap-3 text-[#684834]">
                  <Clock3 className="size-7 text-[#ff7182]" strokeWidth={2.2} aria-hidden />
                  제한 시간
                </h3>
                <div className="mt-6 grid grid-cols-3 gap-4">
                  {FLIPBOOK_TIME_LIMITS_SECONDS.map((seconds) => (
                    <button
                      key={seconds}
                      type="button"
                      onClick={() => onSelectTimeLimit(seconds)}
                      disabled={!isHost}
                      className={cn(
                        'h3-b min-h-[72px] rounded-[16px] border border-[#f2dece] bg-[#fff2e9] text-[#b79a88] shadow-[0_7px_14px_rgb(155_93_58_/_12%)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60',
                        selectedTimeLimitSeconds === seconds &&
                          'border-[#ff7a8c] bg-[#ff7182] text-white shadow-[0_9px_16px_rgb(255_113_130_/_28%)]',
                      )}
                    >
                      {seconds}초
                    </button>
                  ))}
                </div>
              </section>

              <section className="rounded-[22px] border border-[#efd8c7] bg-white/54 p-6 shadow-[inset_0_1px_0_rgb(255_255_255_/_82%)]">
                <h3 className="h3-b inline-flex items-center gap-3 text-[#684834]">
                  <Flag className="size-7 text-[#ff7182]" strokeWidth={2.2} aria-hidden />
                  라운드
                </h3>
                <div className="mt-7 flex items-center justify-between gap-5">
                  <span className="h3-b text-[#9a7f6d]">설정값</span>
                  <div className="flex items-center gap-6">
                    <button
                      type="button"
                      disabled={!canDecreaseRoundCount}
                      onClick={() => {
                        if (roundCount !== null) onSelectRoundCount(roundCount - 1)
                      }}
                      className="grid size-16 place-items-center rounded-full bg-[#fff2e9] text-[#80543b] shadow-[0_7px_14px_rgb(155_93_58_/_12%)] disabled:cursor-not-allowed disabled:opacity-50"
                      aria-label="라운드 감소"
                    >
                      <Minus className="size-7" strokeWidth={3} aria-hidden />
                    </button>
                    <span className="text-[48px] font-black leading-none text-[#684834]">
                      {roundCount ?? '-'}
                    </span>
                    <button
                      type="button"
                      disabled={roundControlDisabled}
                      onClick={() => {
                        if (roundCount !== null) onSelectRoundCount(roundCount + 1)
                      }}
                      className="grid size-16 place-items-center rounded-full bg-[#fff0ed] text-[#ff7182] shadow-[0_7px_14px_rgb(155_93_58_/_12%)] disabled:cursor-not-allowed disabled:opacity-50"
                      aria-label="라운드 증가"
                    >
                      <Plus className="size-8" strokeWidth={3} aria-hidden />
                    </button>
                  </div>
                </div>
              </section>
            </div>

            <button
              type="button"
              onClick={onStartGame}
              disabled={startGameButtonDisabled}
              aria-label={startGameButtonLabel}
              className="relative mx-auto mt-9 block aspect-[1125/175] w-full max-w-[760px] overflow-hidden rounded-full transition hover:-translate-y-1 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Image
                src={FLIPBOOK_LOBBY_IMAGES.startButton}
                alt=""
                fill
                sizes="(max-width: 1024px) 86vw, 760px"
                className="object-fill"
              />
              {startGameButtonLabel !== '게임 시작!' && (
                <span className="body-l-b absolute inset-0 grid place-items-center bg-[#f78b99]/72 text-white backdrop-blur-[1px]">
                  {startGameButtonLabel}
                </span>
              )}
              <span className="sr-only">{startGameButtonLabel}</span>
            </button>
            {errorMessage && (
              <p className="body-b mt-5 rounded-full bg-white/76 px-5 py-3 text-center text-[#cf5d68] shadow-[0_8px_18px_rgb(126_74_42_/_10%)]">
                {errorMessage}
              </p>
            )}
            </section>
          </main>
        </div>
      </div>
    </section>
  )
}
