'use client'

import { Copy, Minus, Palette, Plus, QrCode, type LucideIcon } from 'lucide-react'
import { useRef, useState } from 'react'
import { cn } from '@/shared/libs'
import type { FlipbookConnectionStatus } from '@/shared/types'
import {
  FLIPBOOK_TIME_LIMITS_SECONDS,
  type FlipbookParticipant,
  type FlipbookTimeLimitSeconds,
} from '../constants'
import FlipbookPaperBackground from './FlipbookPaperBackground'

interface FlipbookLobbyViewProps {
  currentParticipant: FlipbookParticipant
  participants: FlipbookParticipant[]
  roomCode: string | null
  participantCount: number
  maxParticipants: number
  selectedTimeLimitSeconds: number
  roundCount: number
  minimumRoundCount: number
  connectionStatus: FlipbookConnectionStatus
  canStartGame: boolean
  isHost: boolean
  isBusy: boolean
  errorMessage: string | null
  onSelectTimeLimit: (seconds: FlipbookTimeLimitSeconds) => void
  onDecreaseRoundCount: () => void
  onIncreaseRoundCount: () => void
  onStartGame: () => void
}

const SHARE_ACTIONS: { key: ShareActionKey; label: string; Icon: LucideIcon }[] = [
  { key: 'copyLink', label: '링크 복사', Icon: Copy },
  { key: 'qrCode', label: 'QR 코드', Icon: QrCode },
]
type ShareActionKey = 'copyLink' | 'qrCode'
const PARTICIPANT_TILT_CLASSES = [
  '-rotate-1',
  'rotate-[0.8deg]',
  '-rotate-[0.6deg]',
  'rotate-1',
  '-rotate-[0.4deg]',
]

export default function FlipbookLobbyView({
  currentParticipant,
  participants,
  roomCode,
  participantCount,
  maxParticipants,
  selectedTimeLimitSeconds,
  roundCount,
  minimumRoundCount,
  connectionStatus,
  canStartGame,
  isHost,
  isBusy,
  errorMessage,
  onSelectTimeLimit,
  onDecreaseRoundCount,
  onIncreaseRoundCount,
  onStartGame,
}: FlipbookLobbyViewProps) {
  const sessionParticipantName = currentParticipant.name.replace(' (나)', '')
  const waitingSlots = Array.from(
    { length: Math.min(4, Math.max(0, maxParticipants - participantCount)) },
    (_, waitingSlotIndex) => `waiting-${waitingSlotIndex}`,
  )
  const isConnectionReady = connectionStatus === 'connected'

  return (
    <section className="relative min-h-screen overflow-hidden bg-flipbook-background text-flipbook-ink">
      <FlipbookPaperBackground />
      <div className="relative z-10 mx-auto flex min-h-screen w-full max-w-[1160px] flex-col px-5 py-8 sm:px-7">
        <header className="flex flex-col items-center text-center">
          <h1 className="h1-b origin-center scale-125 text-flipbook-ink drop-shadow-[0_5px_0_var(--color-flipbook-shadow)]">
            플립북
          </h1>
          <p className="body-b mt-5 text-flipbook-deep">친구들이 모이면 바로 시작해요</p>
        </header>

        <main className="mt-10 grid flex-1 content-center gap-8 lg:grid-cols-[0.86fr_1.14fr]">
          <aside className="relative -rotate-2 self-center rounded-[7px] border border-flipbook-light bg-flipbook-primary px-7 py-8 text-center shadow-[0_12px_20px_var(--color-flipbook-shadow)]">
            <span
              aria-hidden
              className="absolute left-1/2 top-0 h-7 w-[46%] -translate-x-1/2 -translate-y-4 rotate-1 rounded-[3px] bg-flipbook-light/75 shadow-[0_4px_8px_var(--color-flipbook-shadow)]"
            />
            <div className="rounded-[6px] border border-flipbook-light bg-flipbook-paper/26 px-5 py-8">
              <p className="h4-b text-flipbook-ink">입장 코드</p>
              <p className="h1-b mt-4 text-flipbook-ink">{roomCode ?? '------'}</p>
              <div className="mt-6 flex justify-center gap-3">
                {SHARE_ACTIONS.map((action) => (
                  <ShareButton
                    key={action.key}
                    actionKey={action.key}
                    label={action.label}
                    Icon={action.Icon}
                    roomCode={roomCode}
                  />
                ))}
              </div>
            </div>
          </aside>

          <section className="relative rounded-[7px] border border-flipbook-light bg-flipbook-paper/88 px-6 py-6 shadow-[0_14px_24px_var(--color-flipbook-shadow)]">
            <span
              aria-hidden
              className="absolute left-7 top-0 h-7 w-28 -translate-y-4 -rotate-2 rounded-[3px] bg-flipbook-light/70"
            />
            <span
              aria-hidden
              className="absolute right-10 top-0 h-7 w-24 -translate-y-4 rotate-2 rounded-[3px] bg-flipbook-light/62"
            />

            <div className="grid gap-5">
              <div className="flex items-end justify-between gap-4 border-b border-flipbook-light pb-3">
                <h2 className="h2-b text-flipbook-ink">참여자</h2>
                <span className="h3-b text-flipbook-deep">
                  {participantCount} / {maxParticipants}
                </span>
              </div>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {participants.map((participant, participantIndex) => (
                  <ParticipantNameTag
                    key={participant.userUuid}
                    name={
                      participant.userUuid === currentParticipant.userUuid
                        ? sessionParticipantName
                        : participant.name
                    }
                    isHost={participant.isHost === true}
                    tiltClassName={
                      PARTICIPANT_TILT_CLASSES[
                        participantIndex % PARTICIPANT_TILT_CLASSES.length
                      ]
                    }
                  />
                ))}
                {waitingSlots.map((waitingSlot) => (
                  <div
                    key={waitingSlot}
                    className="caption-b grid min-h-14 place-items-center rounded-[6px] border border-dashed border-flipbook-primary bg-flipbook-paper/65 text-flipbook-muted"
                  >
                    초대 대기중
                  </div>
                ))}
              </div>

              <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_220px]">
                <div className="rounded-[6px] border border-flipbook-light bg-flipbook-paper/72 px-4 py-3">
                  <p className="caption-b text-flipbook-muted">제한 시간</p>
                  <div className="mt-2 grid grid-cols-3 gap-2">
                    {FLIPBOOK_TIME_LIMITS_SECONDS.map((seconds) => (
                      <button
                        key={seconds}
                        type="button"
                        onClick={() => onSelectTimeLimit(seconds)}
                        disabled={!isHost}
                        className={cn(
                          'body-b min-h-11 rounded-[6px] border border-flipbook-light bg-flipbook-light text-flipbook-primary shadow-[0_3px_6px_var(--color-flipbook-shadow)]',
                          selectedTimeLimitSeconds === seconds &&
                            'border-flipbook-deep bg-flipbook-primary text-flipbook-ink',
                        )}
                      >
                        {seconds}초
                      </button>
                    ))}
                  </div>
                </div>

                <div className="flex items-center justify-between gap-4 rounded-[6px] border border-flipbook-light bg-flipbook-light px-4 py-3">
                  <div>
                    <p className="caption-b text-flipbook-muted">라운드</p>
                    <p className="caption-r mt-1 text-flipbook-muted">
                      최소 {minimumRoundCount}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      aria-label="라운드 줄이기"
                      onClick={onDecreaseRoundCount}
                      disabled={!isHost}
                      className="grid size-10 place-items-center rounded-full bg-flipbook-paper text-flipbook-ink shadow-[0_3px_6px_var(--color-flipbook-shadow)]"
                    >
                      <Minus className="size-4" aria-hidden />
                    </button>
                    <span className="h2-b min-w-8 text-center text-flipbook-ink">
                      {roundCount}
                    </span>
                    <button
                      type="button"
                      aria-label="라운드 늘리기"
                      onClick={onIncreaseRoundCount}
                      disabled={!isHost}
                      className="grid size-10 place-items-center rounded-full bg-flipbook-primary text-flipbook-ink shadow-[0_3px_6px_var(--color-flipbook-shadow)]"
                    >
                      <Plus className="size-4" aria-hidden />
                    </button>
                  </div>
                </div>
              </div>

              <button
                type="button"
                onClick={onStartGame}
                disabled={!canStartGame || !isConnectionReady || isBusy}
                className="body-b mx-auto inline-flex min-h-15 w-full max-w-[420px] items-center justify-center gap-2 rounded-[8px] border-2 border-flipbook-deep bg-flipbook-primary text-flipbook-ink shadow-[0_8px_14px_var(--color-flipbook-shadow)]"
              >
                <Palette className="size-5" aria-hidden />
                {isBusy ? '시작 중' : '게임 시작'}
              </button>
              {errorMessage && (
                <p className="caption-b text-center text-flipbook-deep">{errorMessage}</p>
              )}
            </div>
          </section>
        </main>
      </div>
    </section>
  )
}

function ParticipantNameTag({
  name,
  isHost,
  tiltClassName,
}: {
  name: string
  isHost: boolean
  tiltClassName: string
}) {
  return (
    <div
      className={cn(
        'flex min-h-14 items-center rounded-[6px] border border-flipbook-light bg-flipbook-light px-5 shadow-[0_5px_8px_var(--color-flipbook-shadow)]',
        tiltClassName,
      )}
    >
      <span className="body-l-b flex-1 text-flipbook-ink">{name}</span>
      {isHost && <span className="caption-b text-flipbook-deep">방장</span>}
    </div>
  )
}

function ShareButton({
  actionKey,
  label,
  Icon,
  roomCode,
}: {
  actionKey: ShareActionKey
  label: string
  Icon: LucideIcon
  roomCode: string | null
}) {
  const [copyLabel, setCopyLabel] = useState(label)
  const resetLabelTimerRef = useRef<number | null>(null)

  const copyTextWithFallback = async (text: string) => {
    if (window.navigator.clipboard?.writeText) {
      try {
        await window.navigator.clipboard.writeText(text)
        return
      } catch {
        // 브라우저 권한 정책으로 Clipboard API가 거부되면 DOM 기반 복사로 한 번 더 시도한다.
      }
    }

    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', '')
    textarea.style.position = 'fixed'
    textarea.style.top = '-9999px'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.focus()
    textarea.select()
    textarea.setSelectionRange(0, text.length)
    const copied = document.execCommand('copy')
    document.body.removeChild(textarea)

    if (!copied) {
      throw new Error('클립보드 복사에 실패했습니다.')
    }
  }

  const createShareUrl = () => {
    const shareUrl = new URL(window.location.href)
    shareUrl.searchParams.set('roomCode', roomCode ?? '')
    shareUrl.hash = ''
    return shareUrl.toString()
  }

  const copyShareText = () => {
    if (!roomCode || typeof window === 'undefined') return
    const copyText = actionKey === 'copyLink' ? createShareUrl() : roomCode

    void (async () => {
      try {
        await copyTextWithFallback(copyText)
        setCopyLabel('복사됨')

        if (resetLabelTimerRef.current) {
          window.clearTimeout(resetLabelTimerRef.current)
        }

        resetLabelTimerRef.current = window.setTimeout(() => {
          setCopyLabel(label)
          resetLabelTimerRef.current = null
        }, 1400)
      } catch {
        setCopyLabel('복사 실패')
      }
    })()
  }

  return (
    <button
      type="button"
      onClick={copyShareText}
      disabled={!roomCode}
      className="body-b inline-flex min-h-[45px] items-center gap-1.5 rounded-[8px] border border-flipbook-primary bg-flipbook-light px-4 text-flipbook-deep"
    >
      <Icon className="size-[17px]" aria-hidden />
      {copyLabel}
    </button>
  )
}
