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
    <aside className="fixed right-5 top-1/2 z-10 h-[min(430px,calc(100vh-40px))] w-80 -translate-y-1/2 text-[#24366c]">
      <Image
        src="/images/infinite-canvas/participant-panel-seamless-full.png"
        alt=""
        aria-hidden
        fill
        priority
        sizes="320px"
        className="object-fill drop-shadow-[0_18px_34px_rgba(55,82,190,0.2)]"
      />
      <div className="relative z-10 flex h-full flex-col gap-4 px-8 py-7">
        <div className="mb-3 flex items-center justify-between gap-3">
          <p className="body-b text-[#25376c]">✦ 참여자</p>
          <span className="caption-b rounded-full border border-white/90 bg-white/84 px-4 py-1.5 text-[#31518f] shadow-[0_8px_16px_rgba(93,114,255,0.16),inset_0_1px_0_rgba(255,255,255,0.85)]">
            {participants.length}/{participantLimit}
          </span>
        </div>
        <ul className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto pr-1">
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
                className="flex min-w-0 items-center gap-3 rounded-[22px] border border-white/82 bg-white/70 px-4 py-3 shadow-[0_7px_16px_rgba(65,95,160,0.12),inset_0_1px_0_rgba(255,255,255,0.86)]"
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
        <div className="h-px bg-white/72" />
        <div className="flex items-center justify-between gap-3 rounded-full border border-white/82 bg-white/64 px-4 py-2.5 shadow-[inset_0_1px_0_rgba(255,255,255,0.86)]">
          <span className="caption-b text-[#49679d]">상태</span>
          <span className="caption-b text-[#31518f]">{getConnectionText(connectionStatus)}</span>
        </div>
      </div>
    </aside>
  )
}
