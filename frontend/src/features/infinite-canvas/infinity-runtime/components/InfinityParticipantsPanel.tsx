'use client'

import { useState } from 'react'
import Image from 'next/image'
import type { InfiniteCanvasConnectionStatus, InfiniteCanvasParticipantResponse } from '@/shared/types'

import { INFINITE_CANVAS_COLOR_OPTIONS } from '../../constants'

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
  isUpdatingProfile?: boolean
  onUpdateMyColor?: (color: string) => Promise<boolean>
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
  isUpdatingProfile = false,
  onUpdateMyColor,
}: InfinityParticipantsPanelProps) {
  const [isColorPickerOpen, setIsColorPickerOpen] = useState(false)
  const sortedParticipants = [...participants].sort((first, second) => {
    if (first.userUuid === me?.userUuid) return -1
    if (second.userUuid === me?.userUuid) return 1
    return first.joinedAt.localeCompare(second.joinedAt)
  })
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

            return (
              <li
                key={participant.userUuid}
                className="relative flex h-[42px] min-w-0 shrink-0 items-center gap-3 rounded-full border border-white/82 bg-white/76 px-3.5 py-2 shadow-[0_7px_14px_rgba(65,95,160,0.11),inset_0_1px_0_rgba(255,255,255,0.9)]"
              >
                {isMe && onUpdateMyColor ? (
                  <button
                    type="button"
                    title="내 색상 변경"
                    disabled={isUpdatingProfile}
                    onClick={() => setIsColorPickerOpen((open) => !open)}
                    className="grid size-6 shrink-0 place-items-center rounded-full border-2 border-white transition-transform hover:scale-110 disabled:opacity-60"
                    style={{
                      backgroundColor: participant.color,
                      boxShadow:
                        '0 0 0 3px #25376c, 0 0 0 6px rgba(255,242,160,0.78), 0 6px 14px rgba(93,114,255,0.26)',
                    }}
                  >
                    <span className="sr-only">내 색상 변경</span>
                  </button>
                ) : (
                  <span
                    className="size-4.5 shrink-0 rounded-full border-2 border-white"
                    style={{
                      backgroundColor: participant.color,
                      boxShadow:
                        '0 0 0 1px rgba(75,105,170,0.18), 0 5px 12px rgba(93,114,255,0.18)',
                    }}
                  />
                )}
                <span
                  className="body-b min-w-0 flex-1 truncate text-[#25376c]"
                  title={displayNickname}
                >
                  {displayNickname}
                </span>
                {participant.host && (
                  <span className="caption-b shrink-0 rounded-full bg-[#fff0ba] px-2 py-1 text-[#936019]">
                    방장
                  </span>
                )}
                <span className="caption-b shrink-0 rounded-full bg-[#eef6ff] px-2.5 py-1 text-[#4873b5]">
                  {isConnected ? '접속' : '오프'}
                </span>
                {isMe && isColorPickerOpen && onUpdateMyColor && (
                  <div className="absolute left-2 top-[48px] z-20 grid grid-cols-4 gap-2 rounded-[18px] border border-white/80 bg-white/92 p-3 shadow-[0_16px_26px_rgba(60,82,160,0.22)] backdrop-blur">
                    {INFINITE_CANVAS_COLOR_OPTIONS.map(({ value: color, label }) => {
                      const selected = color.toLowerCase() === participant.color.toLowerCase()
                      return (
                        <button
                          key={color}
                          type="button"
                          title={`${label}로 변경`}
                          disabled={isUpdatingProfile}
                          onClick={async () => {
                            const updated = await onUpdateMyColor(color)
                            if (updated) setIsColorPickerOpen(false)
                          }}
                          className="size-7 rounded-full border-2 border-white transition-transform hover:scale-110 disabled:opacity-60"
                          style={{
                            backgroundColor: color,
                            boxShadow: selected
                              ? '0 0 0 3px #ff5f9a, 0 6px 12px rgba(70,80,160,0.22)'
                              : '0 0 0 1px rgba(60,80,140,0.12), 0 5px 10px rgba(70,80,160,0.14)',
                          }}
                        >
                          <span className="sr-only">{color} 선택</span>
                        </button>
                      )
                    })}
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      </div>
    </aside>
  )
}
