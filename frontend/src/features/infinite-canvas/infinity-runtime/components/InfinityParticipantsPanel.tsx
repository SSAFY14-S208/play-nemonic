'use client'

import Image from 'next/image'
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
    <aside className="fixed right-5 top-1/2 z-10 h-[176px] w-[300px] -translate-y-1/2 text-[#24366c]">
      <Image
        src="/images/infinite-canvas/participant-panel-stretch-clean-full.png"
        alt=""
        aria-hidden
        fill
        priority
        sizes="300px"
        className="object-fill drop-shadow-[0_16px_28px_rgba(55,82,190,0.18)]"
      />
      <div className="relative z-10 flex h-full flex-col px-7 py-4">
        <div className="flex items-center justify-between gap-3">
          <p className="body-b text-[#25376c]">참여자</p>
          <span className="caption-b rounded-full border border-white/90 bg-white/88 px-3.5 py-1.5 text-[#31518f] shadow-[0_8px_16px_rgba(93,114,255,0.14),inset_0_1px_0_rgba(255,255,255,0.9)]">
            {participants.length}/{participantLimit}
          </span>
        </div>
        <ul className="mt-2.5 flex max-h-[58px] min-h-0 flex-col gap-1.5 overflow-y-auto pr-1">
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
                className="flex min-w-0 items-center gap-3 rounded-full border border-white/82 bg-white/76 px-3.5 py-2 shadow-[0_7px_14px_rgba(65,95,160,0.11),inset_0_1px_0_rgba(255,255,255,0.9)]"
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
                <span className="caption-b shrink-0 rounded-full bg-[#eef6ff] px-2.5 py-1 text-[#4873b5]">
                  {participant.connected ? '접속' : '오프'}
                </span>
              </li>
            )
          })}
        </ul>
        <div className="mt-auto flex items-center justify-between gap-3 rounded-full border border-white/82 bg-white/68 px-4 py-2 shadow-[inset_0_1px_0_rgba(255,255,255,0.88)]">
          <span className="caption-b text-[#49679d]">연결 상태</span>
          <span className="caption-b text-[#31518f]">{getConnectionText(connectionStatus)}</span>
        </div>
      </div>
    </aside>
  )
}
