'use client'

import { useState } from 'react'
import type { InfiniteCanvasConnectionStatus, InfiniteCanvasParticipantResponse } from '@/shared/types'

import { INFINITE_CANVAS_COLOR_OPTIONS } from '../..'

const PANEL_WIDTH = 324
const PARTICIPANT_ROW_HEIGHT = 42
const PARTICIPANT_ROW_GAP = 6

interface InfinityParticipantsPanelProps {
  connectionStatus: InfiniteCanvasConnectionStatus
  maxParticipants: number
  me: InfiniteCanvasParticipantResponse | null
  myUserUuid: string | null
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
  myUserUuid,
  participants,
  isUpdatingProfile = false,
  onUpdateMyColor,
}: InfinityParticipantsPanelProps) {
  const [isColorPickerOpen, setIsColorPickerOpen] = useState(false)
  const currentUserUuid = myUserUuid ?? me?.userUuid ?? null
  const sortedParticipants = [...participants].sort((first, second) => {
    if (first.userUuid === currentUserUuid) return -1
    if (second.userUuid === currentUserUuid) return 1
    return first.joinedAt.localeCompare(second.joinedAt)
  })
  const connectedParticipantCount = participants.filter((participant) =>
    isCurrentUserConnected(participant, me, connectionStatus),
  ).length
  const displayedConnectedCount =
    connectedParticipantCount > 0 ? connectedParticipantCount : participants.length
  const capacityText =
    maxParticipants > 0 ? `${displayedConnectedCount}명 접속 / 최대 ${maxParticipants}명` : '접속 정보 확인 중'
  const participantListMaxHeight =
    Math.max(sortedParticipants.length, 1) * PARTICIPANT_ROW_HEIGHT +
    Math.max(0, sortedParticipants.length - 1) * PARTICIPANT_ROW_GAP

  return (
    <aside
      className="fixed right-5 top-1/2 z-10 -translate-y-1/2 text-[#24366c]"
      style={{ width: PANEL_WIDTH }}
    >
      <div className="relative overflow-visible rounded-[28px] border border-white/72 bg-[#3aa7f4] p-4 shadow-[0_16px_30px_rgba(46,95,210,0.22),inset_0_1px_0_rgba(255,255,255,0.44)]">
        <div className="pointer-events-none absolute inset-0 rounded-[28px] bg-white/8" />
        <div className="pointer-events-none absolute -left-10 -top-8 size-28 rounded-full bg-white/18 blur-xl" />
        <div className="relative z-10 flex justify-end">
          <span className="body-b px-1.5 py-1 text-white drop-shadow-[0_2px_6px_rgba(29,48,135,0.55)]">
            {capacityText}
          </span>
        </div>
        <ul
          className="relative z-10 mt-3 flex min-h-0 flex-col gap-1.5 overflow-y-auto pr-1"
          style={{ maxHeight: Math.min(participantListMaxHeight, 318) }}
        >
          {sortedParticipants.map((participant) => {
            const isMe = participant.userUuid === currentUserUuid
            const isConnected = isCurrentUserConnected(participant, me, connectionStatus)
            const displayNickname = `${participant.nickname}${isMe ? ' (나)' : ''}`
            const statusLabel = isConnected ? '접속 중' : '오프라인'

            return (
              <li
                key={participant.userUuid}
                className="relative flex h-[42px] min-w-0 shrink-0 items-center gap-3 rounded-full border border-white/82 bg-[#e8f7ff]/88 px-3.5 py-2 shadow-[0_7px_14px_rgba(65,95,160,0.1),inset_0_1px_0_rgba(255,255,255,0.86)]"
              >
                {isMe && onUpdateMyColor ? (
                  <button
                    type="button"
                    title="내 색상 변경"
                    disabled={isUpdatingProfile}
                    onClick={() => setIsColorPickerOpen((open) => !open)}
                    className="grid size-4.5 shrink-0 place-items-center rounded-full border-2 border-white transition-transform hover:scale-110 disabled:opacity-60"
                    style={{
                      backgroundColor: participant.color,
                      boxShadow:
                        '0 0 0 3px #25376c, 0 5px 12px rgba(93,114,255,0.22)',
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
                <span
                  className="grid size-3 shrink-0 place-items-center rounded-full bg-white/85"
                  title={statusLabel}
                  aria-label={statusLabel}
                >
                  <span
                    className={`size-2 rounded-full ${
                      isConnected
                        ? 'bg-[#28d86c] shadow-[0_0_0_2px_rgba(40,216,108,0.16),0_0_10px_rgba(40,216,108,0.72)]'
                        : 'bg-[#b9c4d6]'
                    }`}
                  />
                </span>
              </li>
            )
          })}
        </ul>
      </div>
      {isColorPickerOpen && me && onUpdateMyColor && (
        <>
          <button
            type="button"
            aria-label="색상 선택 닫기"
            className="fixed inset-0 z-20 cursor-default bg-transparent"
            onClick={() => setIsColorPickerOpen(false)}
          />
          <div className="absolute bottom-full right-0 z-30 mb-3 min-w-[292px] rounded-[24px] border border-white/80 bg-white/94 p-4 shadow-[0_18px_34px_rgba(48,76,160,0.24)] backdrop-blur-md">
            <p className="body-b mb-3 text-[#25376c]">내 색상 선택</p>
            <div className="grid grid-cols-7 gap-2.5">
              {INFINITE_CANVAS_COLOR_OPTIONS.map(({ value: color, label }) => {
                const selected = color.toLowerCase() === me.color.toLowerCase()
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
                    className="size-8 rounded-full border-2 border-white transition-transform hover:scale-110 disabled:opacity-60"
                    style={{
                      backgroundColor: color,
                      boxShadow: selected
                        ? '0 0 0 3px #ff5f9a, 0 8px 14px rgba(70,80,160,0.24)'
                        : '0 0 0 1px rgba(60,80,140,0.12), 0 6px 12px rgba(70,80,160,0.14)',
                    }}
                  >
                    <span className="sr-only">{color} 선택</span>
                  </button>
                )
              })}
            </div>
          </div>
        </>
      )}
    </aside>
  )
}
