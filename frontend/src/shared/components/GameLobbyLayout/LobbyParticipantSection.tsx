'use client'

import { Crown, UsersRound, X } from 'lucide-react'

import { cn } from '@/shared/libs'

import type { GameLobbyTheme, LobbyParticipant } from './GameLobbyLayout.types'

interface LobbyParticipantSectionProps {
  theme: GameLobbyTheme
  participants: LobbyParticipant[]
  maxParticipants: number
  minParticipants: number
  currentUserUuid: string | null
  participantListMaxHeight?: number
  waitingSlotText?: string
  isHost: boolean
  kickingTargetUuid?: string | null
  kickError?: string | null
  onKickParticipant?: (targetUserUuid: string) => void
  variant: 'mobile' | 'desktop'
}

export function LobbyParticipantSection({
  theme,
  participants,
  maxParticipants,
  minParticipants,
  currentUserUuid,
  participantListMaxHeight,
  waitingSlotText = '초대를 기다리는 중...',
  isHost,
  kickingTargetUuid,
  kickError,
  onKickParticipant,
  variant,
}: LobbyParticipantSectionProps) {
  const waitingSlotCount = Math.max(0, maxParticipants - participants.length)
  const isDesktop = variant === 'desktop'

  return (
    <>
      <div
        className="flex items-center justify-between border-b pb-4"
        style={{ borderColor: theme.line }}
      >
        <h2
          className="h3-b inline-flex items-center gap-2"
          style={{ color: theme.ink }}
        >
          <UsersRound
            className={isDesktop ? 'size-6' : 'size-5'}
            strokeWidth={isDesktop ? 2.2 : undefined}
            aria-hidden
          />
          참여자
        </h2>
        <span className="h2-b" style={{ color: theme.accent }}>
          {participants.length} / {maxParticipants}
        </span>
      </div>

      <p className="caption-b mt-2" style={{ color: theme.muted }}>
        {isDesktop
          ? `최소 ${minParticipants}명부터 시작할 수 있어요.`
          : `최소 ${minParticipants}명 필요`}
      </p>

      <div
        className={cn(
          'mt-4 grid gap-3',
          isDesktop && 'grid-cols-1 xl:grid-cols-2',
        )}
        style={
          participantListMaxHeight
            ? { maxHeight: participantListMaxHeight, overflowY: 'auto' as const }
            : undefined
        }
      >
        {participants.map((participant) => (
          <ParticipantTile
            key={participant.userUuid}
            theme={theme}
            participant={participant}
            isMe={participant.userUuid === currentUserUuid}
            canKick={isHost && participant.userUuid !== currentUserUuid}
            isKicking={kickingTargetUuid === participant.userUuid}
            onKick={() => onKickParticipant?.(participant.userUuid)}
          />
        ))}
        {Array.from({ length: waitingSlotCount }).map(
          (_, waitingSlotIndex) => (
            <div
              key={`waiting-${waitingSlotIndex}`}
              className="body-l grid min-h-11 place-items-center rounded-2xl border border-dashed px-5 py-3"
              style={{
                borderColor: theme.accent,
                color: theme.dash ?? theme.muted,
              }}
            >
              {waitingSlotText}
            </div>
          ),
        )}
      </div>

      {kickError && (
        <p role="alert" className="caption-r mt-3 text-error">
          {kickError}
        </p>
      )}
    </>
  )
}

// ── 참여자 타일 ──

interface ParticipantTileProps {
  theme: GameLobbyTheme
  participant: LobbyParticipant
  isMe: boolean
  canKick: boolean
  isKicking: boolean
  onKick: () => void
}

function ParticipantTile({
  theme,
  participant,
  isMe,
  canKick,
  isKicking,
  onKick,
}: ParticipantTileProps) {
  const avatarChar = participant.nickname.slice(0, 1).toUpperCase()

  return (
    <div
      className={cn(
        'flex min-h-11 items-center gap-2.5 rounded-2xl border px-5 py-3',
        !participant.connected && 'opacity-60',
      )}
      style={{
        borderColor: theme.line,
        backgroundColor: theme.active ?? theme.paper,
      }}
    >
      <span
        className="grid size-7 place-items-center rounded-full text-[12px] font-bold"
        style={{ backgroundColor: theme.paper, color: theme.ink }}
      >
        {avatarChar}
      </span>
      <span className="body-l flex-1 truncate" style={{ color: theme.ink }}>
        {participant.nickname}
        {isMe && ' (나)'}
      </span>
      {!participant.connected && (
        <span className="caption-r" style={{ color: theme.muted }}>
          재연결 중...
        </span>
      )}
      {participant.host && (
        <span
          className="caption-b inline-flex items-center gap-1 rounded-full border px-2 py-1"
          style={{
            borderColor: theme.accent,
            backgroundColor: theme.accent,
            color: theme.ink,
          }}
        >
          <Crown className="size-4" aria-hidden />
          방장
        </span>
      )}
      {canKick && (
        <button
          type="button"
          onClick={onKick}
          disabled={isKicking}
          aria-label={`${participant.nickname} 강퇴`}
          className="grid size-7 cursor-pointer place-items-center rounded-full transition-colors hover:text-error disabled:cursor-not-allowed disabled:opacity-50"
          style={{ color: theme.muted }}
        >
          <X className="size-4" aria-hidden />
        </button>
      )}
    </div>
  )
}
