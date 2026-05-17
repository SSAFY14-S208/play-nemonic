'use client'

import Image from 'next/image'
import type { InfiniteCanvasConnectionStatus, InfiniteCanvasParticipantResponse } from '@/shared/types'
import { INFINITY_PARTICIPANT_ACCENTS } from '../constants'

const PANEL_WIDTH = 334
const PANEL_TOP_HEIGHT = 58
const PANEL_BOTTOM_HEIGHT = 48
const PANEL_MIN_MIDDLE_HEIGHT = 68
const PARTICIPANT_ROW_HEIGHT = 42
const PARTICIPANT_ROW_GAP = 6
const PARTICIPANT_LIST_VERTICAL_PADDING = 16

interface InfinityParticipantsPanelProps {
  connectionStatus: InfiniteCanvasConnectionStatus
  maxParticipants: number
  me: InfiniteCanvasParticipantResponse | null
  participants: InfiniteCanvasParticipantResponse[]
}

function isCurrentUserConnected(
  participant: InfiniteCanvasParticipantResponse,
  me: InfiniteCanvasParticipantResponse | null,
  connectionStatus: InfiniteCanvasConnectionStatus,
) {
  if (participant.connected) return true
  return participant.userUuid === me?.userUuid && connectionStatus === 'connected'
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
  const connectedParticipantCount = participants.filter((participant) => participant.connected).length
  const displayedConnectedCount =
    connectedParticipantCount > 0 ? connectedParticipantCount : participants.length
  const capacityText =
    maxParticipants > 0 ? `${displayedConnectedCount}명 접속 / 최대 ${maxParticipants}명` : '접속 정보 확인 중'
  const participantRowCount = Math.max(sortedParticipants.length, 1)
  const participantListHeight =
    participantRowCount * PARTICIPANT_ROW_HEIGHT +
    Math.max(0, participantRowCount - 1) * PARTICIPANT_ROW_GAP +
    PARTICIPANT_LIST_VERTICAL_PADDING
  const panelMiddleHeight = Math.max(PANEL_MIN_MIDDLE_HEIGHT, participantListHeight)
  const panelHeight = PANEL_TOP_HEIGHT + panelMiddleHeight + PANEL_BOTTOM_HEIGHT

  return (
    <aside
      className="fixed right-5 top-1/2 z-10 -translate-y-1/2 text-[#24366c]"
      style={{ width: PANEL_WIDTH, height: panelHeight }}
    >
      <div className="absolute inset-0 flex flex-col drop-shadow-[0_16px_28px_rgba(55,82,190,0.18)]">
        <div className="relative shrink-0" style={{ height: PANEL_TOP_HEIGHT }}>
          <Image
            src="/images/infinite-canvas/participant-panel-top-clean.png"
            alt=""
            aria-hidden
            fill
            priority
            sizes={`${PANEL_WIDTH}px`}
            className="object-fill"
          />
        </div>
        <div className="relative shrink-0" style={{ height: panelMiddleHeight }}>
          <Image
            src="/images/infinite-canvas/participant-panel-middle-clean.png"
            alt=""
            aria-hidden
            fill
            priority
            sizes={`${PANEL_WIDTH}px`}
            className="object-fill"
          />
        </div>
        <div className="relative shrink-0" style={{ height: PANEL_BOTTOM_HEIGHT }}>
          <Image
            src="/images/infinite-canvas/participant-panel-bottom-clean.png"
            alt=""
            aria-hidden
            fill
            priority
            sizes={`${PANEL_WIDTH}px`}
            className="object-fill"
          />
        </div>
      </div>
      <div className="relative z-10 h-full px-8">
        <span className="body-b absolute right-8 top-8 px-1.5 py-1 text-white drop-shadow-[0_2px_6px_rgba(29,48,135,0.55)]">
          {capacityText}
        </span>
        <ul className="absolute inset-x-8 bottom-9 top-[74px] flex min-h-0 flex-col gap-1.5 overflow-y-auto pr-1">
          {sortedParticipants.map((participant) => {
            const isMe = participant.userUuid === me?.userUuid
            const isConnected = isCurrentUserConnected(participant, me, connectionStatus)
            const displayNickname = `${participant.nickname}${isMe ? ' (나)' : ''}`
            const identityIndex = participantIdentityIndexes.get(participant.userUuid) ?? 0
            const accentColor =
              INFINITY_PARTICIPANT_ACCENTS[
                identityIndex % INFINITY_PARTICIPANT_ACCENTS.length
              ]

            return (
              <li
                key={participant.userUuid}
                className="flex h-[42px] min-w-0 shrink-0 items-center gap-3 rounded-full border border-white/82 bg-white/76 px-3.5 py-2 shadow-[0_7px_14px_rgba(65,95,160,0.11),inset_0_1px_0_rgba(255,255,255,0.9)]"
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
                  {isConnected ? '접속' : '오프'}
                </span>
              </li>
            )
          })}
        </ul>
      </div>
    </aside>
  )
}
