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
  const participantLimit = maxParticipants || participants.length

  return (
    <aside className="fixed right-5 top-1/2 z-10 flex w-72 -translate-y-1/2 flex-col gap-4 rounded-[34px] border-2 border-white/80 bg-[linear-gradient(180deg,rgba(255,255,255,0.98),rgba(223,235,255,0.9)_48%,rgba(215,205,255,0.82))] p-5 text-[#24366c] shadow-[0_20px_42px_rgba(55,82,190,0.24),0_0_0_6px_rgba(118,166,255,0.12),inset_0_1px_0_rgba(255,255,255,0.98)] backdrop-blur-md">
      <div>
        <div className="mb-3 flex items-center justify-between gap-3">
          <p className="body-b text-[#25376c]">✦ 참여자</p>
          <span className="caption-b rounded-full border border-white/90 bg-[linear-gradient(135deg,#5dc7f2,#8f6dff)] px-4 py-1.5 text-white shadow-[0_8px_16px_rgba(93,114,255,0.24),inset_0_1px_0_rgba(255,255,255,0.48)]">
            {participants.length}/{participantLimit}
          </span>
        </div>
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
              <li
                key={participant.userUuid}
                className="flex min-w-0 items-center gap-3 rounded-[22px] border border-white/86 bg-white/78 px-4 py-3 shadow-[0_9px_18px_rgba(65,95,160,0.14),inset_0_1px_0_rgba(255,255,255,0.92)]"
              >
                <span
                  className="size-4.5 shrink-0 rounded-full border-2 border-white"
                  style={{
                    backgroundColor: participant.color,
                    boxShadow: `0 0 0 3px ${accentColor}, 0 5px 12px rgba(93, 114, 255, 0.22)`,
                  }}
                />
                <span
                  className="body-b min-w-0 flex-1 truncate text-[#25376c]"
                  title={displayNickname}
                >
                  {displayNickname}
                </span>
                <span
                  className="ml-auto size-3 shrink-0 rounded-full shadow-[0_0_0_4px_rgba(255,255,255,0.78),0_0_12px_rgba(93,199,242,0.72)]"
                  style={{ backgroundColor: participant.connected ? '#5dc7f2' : '#b3ccec' }}
                />
              </li>
            )
          })}
        </ul>
      </div>
      <div className="h-px bg-white/72" />
      <div className="flex items-center justify-between gap-3 rounded-full border border-white/86 bg-white/72 px-4 py-2.5 shadow-[inset_0_1px_0_rgba(255,255,255,0.95)]">
        <span className="caption-b text-[#49679d]">상태</span>
        <span className="caption-b text-[#31518f]">{getConnectionText(connectionStatus)}</span>
      </div>
    </aside>
  )
}
