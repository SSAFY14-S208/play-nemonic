'use client'

import type { InfiniteCanvasConnectionStatus, InfiniteCanvasParticipantResponse } from '@/shared/types'
import { INFINITY_PARTICIPANT_ACCENTS } from '../constants'

interface InfinityParticipantsPanelProps {
  connectionStatus: InfiniteCanvasConnectionStatus
  maxParticipants: number
  me: InfiniteCanvasParticipantResponse | null
  participants: InfiniteCanvasParticipantResponse[]
}

function getConnectionText(connectionStatus: InfiniteCanvasConnectionStatus) {
  if (connectionStatus === 'connected') return '연결됨'
  if (connectionStatus === 'connecting') return '연결 중'
  if (connectionStatus === 'reconnecting') return '재연결 중'
  if (connectionStatus === 'rejected') return '연결 거부'
  return '오프라인'
}

export function InfinityParticipantsPanel({
  connectionStatus,
  maxParticipants,
  me,
  participants,
}: InfinityParticipantsPanelProps) {
  const sortedParticipants = [...participants].sort((first, second) => {
    if (first.userUuid === me?.userUuid) return -1
    if (second.userUuid === me?.userUuid) return 1
    return first.joinedAt.localeCompare(second.joinedAt)
  })
  const participantIdentityIndexes = new Map(
    [...participants]
      .sort((first, second) => first.joinedAt.localeCompare(second.joinedAt))
      .map((participant, participantIndex) => [participant.userUuid, participantIndex]),
  )

  return (
    <aside className="fixed right-4 top-1/2 z-10 flex w-56 -translate-y-1/2 flex-col gap-4 rounded-2xl border border-canvas-border bg-canvas-panel p-4 shadow-[0_14px_32px_rgb(67_102_148_/_14%)]">
      <div>
        <p className="caption-m mb-3 text-canvas-muted">
          참여자 ({participants.length}/{maxParticipants || participants.length})
        </p>
        <ul className="flex flex-col gap-2">
          {sortedParticipants.map((participant) => {
            const isMe = participant.userUuid === me?.userUuid
            const displayNickname = `${participant.nickname}${isMe ? ' (나)' : ''}`
            const identityIndex = participantIdentityIndexes.get(participant.userUuid) ?? 0
            const accentColor =
              INFINITY_PARTICIPANT_ACCENTS[
                identityIndex % INFINITY_PARTICIPANT_ACCENTS.length
              ]

            return (
              <li key={participant.userUuid} className="flex min-w-0 items-center gap-2">
                <span
                  className="size-3 shrink-0 rounded-full border border-white"
                  style={{
                    backgroundColor: participant.color,
                    boxShadow: `0 0 0 2px ${accentColor}`,
                  }}
                />
                <span
                  className="body-b min-w-0 flex-1 truncate text-canvas-ink"
                  title={displayNickname}
                >
                  {displayNickname}
                </span>
                <span
                  className="ml-auto size-2 shrink-0 rounded-full"
                  style={{ backgroundColor: participant.connected ? '#52b878' : '#b3ccec' }}
                />
              </li>
            )
          })}
        </ul>
      </div>
      <div className="h-px bg-canvas-border" />
      <p className="caption-m text-canvas-muted">{getConnectionText(connectionStatus)}</p>
    </aside>
  )
}
