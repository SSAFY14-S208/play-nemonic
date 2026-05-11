'use client'

import { Clock3, Flag, Minus, Plus, UsersRound, type LucideIcon } from 'lucide-react'
import { cn } from '@/shared/libs'
import { FLIPBOOK_TIME_LIMITS_SECONDS } from '../../constants'
import type { FlipbookShareActionKey } from '../../hooks'
import type { FlipbookParticipant, FlipbookTimeLimitSeconds } from '../../types'
import FlipbookLobbyShareButton from '../FlipbookLobbyShareButton'
import { ParticipantNameTag, WaitingParticipantSlot } from './FlipbookLobbyParticipants'

export interface FlipbookLobbyImageSet {
  copyLinkButton: string
  qrCodeButton: string
  plus: string
}

export interface FlipbookLobbyShareAction {
  key: FlipbookShareActionKey
  label: string
  Icon: LucideIcon
}

export default function FlipbookMobileLobbyLayout({
  currentParticipant,
  displayedParticipants,
  sessionParticipantName,
  waitingSlots,
  roomCode,
  visibleParticipantCount,
  maxParticipants,
  selectedTimeLimitSeconds,
  roundCount,
  minimumRoundCount,
  isHost,
  canDecreaseRoundCount,
  roundControlDisabled,
  startGameButtonDisabled,
  startGameButtonLabel,
  errorMessage,
  shareActions,
  images,
  onSelectTimeLimit,
  onSelectRoundCount,
  onStartGame,
}: {
  currentParticipant: FlipbookParticipant
  displayedParticipants: FlipbookParticipant[]
  sessionParticipantName: string
  waitingSlots: string[]
  roomCode: string | null
  visibleParticipantCount: number
  maxParticipants: number
  selectedTimeLimitSeconds: number
  roundCount: number | null
  minimumRoundCount: number
  isHost: boolean
  canDecreaseRoundCount: boolean
  roundControlDisabled: boolean
  startGameButtonDisabled: boolean
  startGameButtonLabel: string
  errorMessage: string | null
  shareActions: FlipbookLobbyShareAction[]
  images: FlipbookLobbyImageSet
  onSelectTimeLimit: (seconds: FlipbookTimeLimitSeconds) => void
  onSelectRoundCount: (roundCount: number) => void
  onStartGame: () => void
}) {
  return (
    <div className="relative z-10 grid w-full gap-4 px-4 pb-8 pt-20 lg:hidden">
      <section className="rounded-[28px] border border-[#efd8c7] bg-white/78 p-5 text-center shadow-[0_14px_32px_rgb(126_74_42_/_14%)] backdrop-blur-sm">
        <p className="h2-b text-[#684834]">플립북</p>
        <p className="body-b mt-2 text-[#b19686]">친구들이 모이면 바로 시작해요!</p>
        <div className="mt-5 rounded-[22px] border border-[#e9cdb8] bg-[#fffaf3]/90 px-4 py-6">
          <p className="body-b text-[#684834]">입장 코드</p>
          <p className="mt-3 break-all text-[44px] font-black leading-none tracking-[0.04em] text-[#684834]">
            {roomCode ?? '------'}
          </p>
          <div className="mt-5 grid grid-cols-2 gap-3">
            {shareActions.map((action) => (
              <FlipbookLobbyShareButton
                key={action.key}
                actionKey={action.key}
                label={action.label}
                Icon={action.Icon}
                roomCode={roomCode}
                images={images}
              />
            ))}
          </div>
        </div>
      </section>

      <section className="rounded-[24px] border border-[#efd8c7] bg-white/82 p-4 shadow-[0_12px_28px_rgb(126_74_42_/_12%)] backdrop-blur-sm">
        <div className="flex items-center justify-between border-b border-[#edd9c9] pb-4">
          <h2 className="h3-b inline-flex items-center gap-2 text-[#684834]">
            <UsersRound className="size-5" aria-hidden />
            참여자
          </h2>
          <span className="h2-b text-[#ff7182]">
            {visibleParticipantCount} / {maxParticipants}
          </span>
        </div>
        <div className="mt-4 grid gap-3">
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
            <WaitingParticipantSlot key={waitingSlot} plusImageSrc={images.plus} />
          ))}
        </div>
      </section>

      <section className="rounded-[24px] border border-[#efd8c7] bg-white/82 p-4 shadow-[0_12px_28px_rgb(126_74_42_/_12%)] backdrop-blur-sm">
        <h3 className="h3-b inline-flex items-center gap-2 text-[#684834]">
          <Clock3 className="size-5 text-[#ff7182]" aria-hidden />
          제한 시간
        </h3>
        <div className="mt-4 grid grid-cols-3 gap-2">
          {FLIPBOOK_TIME_LIMITS_SECONDS.map((seconds) => (
            <button
              key={seconds}
              type="button"
              onClick={() => onSelectTimeLimit(seconds)}
              disabled={!isHost}
              className={cn(
                'body-b min-h-12 rounded-[14px] border border-[#f2dece] bg-[#fff2e9] text-[#b79a88] disabled:cursor-not-allowed disabled:opacity-60',
                selectedTimeLimitSeconds === seconds &&
                  'border-[#ff7a8c] bg-[#ff7182] text-white',
              )}
            >
              {seconds}초
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-[24px] border border-[#efd8c7] bg-white/82 p-4 shadow-[0_12px_28px_rgb(126_74_42_/_12%)] backdrop-blur-sm">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h3 className="h3-b inline-flex items-center gap-2 text-[#684834]">
              <Flag className="size-5 text-[#ff7182]" aria-hidden />
              라운드
            </h3>
            <p className="caption-m mt-1 text-[#9a7f6d]">최소 {minimumRoundCount}라운드</p>
          </div>
          <div className="flex items-center gap-4">
            <button
              type="button"
              disabled={!canDecreaseRoundCount}
              onClick={() => {
                if (roundCount !== null) onSelectRoundCount(roundCount - 1)
              }}
              className="grid size-11 place-items-center rounded-full bg-[#fff2e9] text-[#80543b] disabled:cursor-not-allowed disabled:opacity-50"
              aria-label="라운드 감소"
            >
              <Minus className="size-5" strokeWidth={3} aria-hidden />
            </button>
            <span className="h1-b min-w-8 text-center text-[#684834]">{roundCount ?? '-'}</span>
            <button
              type="button"
              disabled={roundControlDisabled}
              onClick={() => {
                if (roundCount !== null) onSelectRoundCount(roundCount + 1)
              }}
              className="grid size-11 place-items-center rounded-full bg-[#fff0ed] text-[#ff7182] disabled:cursor-not-allowed disabled:opacity-50"
              aria-label="라운드 증가"
            >
              <Plus className="size-6" strokeWidth={3} aria-hidden />
            </button>
          </div>
        </div>
      </section>

      <button
        type="button"
        onClick={onStartGame}
        disabled={startGameButtonDisabled}
        className="body-l-b min-h-14 rounded-[18px] bg-[#ff7182] text-white shadow-[0_12px_24px_rgb(255_113_130_/_24%)] disabled:cursor-not-allowed disabled:opacity-60"
      >
        {startGameButtonLabel}
      </button>
      {errorMessage && (
        <p className="body-b rounded-[18px] bg-white/82 px-4 py-3 text-center text-[#cf5d68]">
          {errorMessage}
        </p>
      )}
    </div>
  )
}
