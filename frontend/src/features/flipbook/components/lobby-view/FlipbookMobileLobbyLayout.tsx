'use client'

import { Clock3, UsersRound, type LucideIcon } from 'lucide-react'
import { cn } from '@/shared/libs'
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
  minParticipants,
  maxParticipants,
  selectedTimeLimitSeconds,
  timeLimitOptions,
  isHost,
  isBusy,
  canLeaveRoom,
  startGameButtonDisabled,
  startGameButtonLabel,
  errorMessage,
  shareActions,
  images,
  onSelectTimeLimit,
  onStartGame,
  onLeaveRoom,
  onKickParticipant,
}: {
  currentParticipant: FlipbookParticipant
  displayedParticipants: FlipbookParticipant[]
  sessionParticipantName: string
  waitingSlots: string[]
  roomCode: string | null
  visibleParticipantCount: number
  minParticipants: number
  maxParticipants: number
  selectedTimeLimitSeconds: number
  timeLimitOptions: FlipbookTimeLimitSeconds[]
  isHost: boolean
  isBusy: boolean
  canLeaveRoom: boolean
  startGameButtonDisabled: boolean
  startGameButtonLabel: string
  errorMessage: string | null
  shareActions: FlipbookLobbyShareAction[]
  images: FlipbookLobbyImageSet
  onSelectTimeLimit: (seconds: FlipbookTimeLimitSeconds) => void
  onStartGame: () => void
  onLeaveRoom: () => void
  onKickParticipant: (targetUserUuid: string) => void
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
        <p className="caption-b mt-3 text-[#9a7f6d]">최소 {minParticipants}명 필요</p>
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
              isConnected={participant.isConnected === true}
              canKick={
                isHost &&
                !isBusy &&
                participant.userUuid !== currentParticipant.userUuid &&
                participant.isHost !== true
              }
              onKick={() => onKickParticipant(participant.userUuid)}
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
          {timeLimitOptions.map((seconds) => (
            <button
              key={seconds}
              type="button"
              onClick={() => onSelectTimeLimit(seconds)}
              disabled={!isHost || isBusy}
              className={cn(
                'body-b min-h-12 rounded-[14px] border border-[#f2dece] bg-[#fff2e9] text-[#b79a88] transition-colors duration-150 ease-out disabled:cursor-not-allowed disabled:opacity-60',
                selectedTimeLimitSeconds === seconds &&
                  'border-[#ff7a8c] bg-[#ff7182] text-white',
              )}
            >
              {seconds}초
            </button>
          ))}
        </div>
      </section>

      {canLeaveRoom && (
        <button
          type="button"
          onClick={onLeaveRoom}
          disabled={isBusy}
          className="body-b min-h-12 rounded-[16px] border border-[#f2dece] bg-[#fff2e9] text-[#80543b] disabled:cursor-not-allowed disabled:opacity-50"
        >
          방 나가기
        </button>
      )}

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
