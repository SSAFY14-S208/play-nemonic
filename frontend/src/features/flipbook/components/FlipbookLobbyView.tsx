'use client'

import Image from 'next/image'
import {
  Clock3,
  Copy,
  Flag,
  HelpCircle,
  Minus,
  Plus,
  QrCode,
  Settings,
  UsersRound,
  type LucideIcon,
} from 'lucide-react'
import { useRef, useState } from 'react'
import { cn } from '@/shared/libs'
import type { FlipbookConnectionStatus } from '@/shared/types'
import {
  FLIPBOOK_TIME_LIMITS_SECONDS,
  type FlipbookParticipant,
  type FlipbookTimeLimitSeconds,
} from '../constants'

interface FlipbookLobbyViewProps {
  currentParticipant: FlipbookParticipant
  participants: FlipbookParticipant[]
  roomCode: string | null
  participantCount: number
  maxParticipants: number
  selectedTimeLimitSeconds: number
  roundCount: number
  connectionStatus: FlipbookConnectionStatus
  canStartGame: boolean
  isHost: boolean
  isBusy: boolean
  errorMessage: string | null
  onSelectTimeLimit: (seconds: FlipbookTimeLimitSeconds) => void
  onStartGame: () => void
}

const FLIPBOOK_LOBBY_IMAGES = {
  background: '/images/flipbook-lobby/background.png',
  startButton: '/images/flipbook-lobby/start-button.png',
  copyLinkButton: '/images/flipbook-lobby/copy-link-button.png',
  qrCodeButton: '/images/flipbook-lobby/qr-code-button.png',
  plus: '/images/flipbook-lobby/plus.svg',
}
const SHARE_ACTIONS: { key: ShareActionKey; label: string; Icon: LucideIcon }[] = [
  { key: 'copyLink', label: '링크 복사', Icon: Copy },
  { key: 'qrCode', label: 'QR 코드', Icon: QrCode },
]
type ShareActionKey = 'copyLink' | 'qrCode'
const VISIBLE_PARTICIPANT_CAPACITY = 6
const SETTINGS_ACTIONS = [
  { key: 'guide', label: '사용 방법', Icon: HelpCircle },
  { key: 'settings', label: '설정', Icon: Settings },
] as const

export default function FlipbookLobbyView({
  currentParticipant,
  participants,
  roomCode,
  participantCount,
  selectedTimeLimitSeconds,
  roundCount,
  connectionStatus,
  canStartGame,
  isHost,
  isBusy,
  errorMessage,
  onSelectTimeLimit,
  onStartGame,
}: FlipbookLobbyViewProps) {
  const sessionParticipantName = currentParticipant.name.replace(' (나)', '')
  const displayedParticipants = participants.slice(0, VISIBLE_PARTICIPANT_CAPACITY)
  const visibleParticipantCount = Math.min(participantCount, VISIBLE_PARTICIPANT_CAPACITY)
  const waitingSlots = Array.from(
    { length: Math.max(0, VISIBLE_PARTICIPANT_CAPACITY - displayedParticipants.length) },
    (_, waitingSlotIndex) => `waiting-${waitingSlotIndex}`,
  )
  const isConnectionReady = connectionStatus === 'connected'
  const startGameButtonDisabled = !isHost || !canStartGame || !isConnectionReady || isBusy
  const startGameButtonLabel = !isHost ? '게임 대기중' : isBusy ? '시작 중' : '게임 시작!'

  return (
    <section className="relative min-h-screen overflow-hidden bg-[#fff5ed] text-[#684834]">
      <Image
        src={FLIPBOOK_LOBBY_IMAGES.background}
        alt=""
        fill
        priority
        sizes="100vw"
        className="object-cover"
      />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_52%_40%,rgb(255_255_255_/_36%),transparent_42%)]" />

      <div className="relative z-10 mx-auto grid min-h-screen w-full max-w-[1720px] grid-rows-[auto_1fr] px-[clamp(20px,4.2vw,80px)] py-[clamp(22px,3.8vw,58px)]">
        <header className="flex flex-wrap justify-end gap-3 sm:gap-6">
          {SETTINGS_ACTIONS.map((action) => (
            <button
              key={action.key}
              type="button"
              className="body-l-b inline-flex h-[56px] items-center gap-3 rounded-full border border-[#ebccb9] bg-white/62 px-5 text-[#684834] shadow-[0_7px_14px_rgb(140_85_58_/_14%),inset_0_1px_0_rgb(255_255_255_/_74%)] backdrop-blur-sm transition hover:-translate-y-0.5 sm:h-[68px] sm:gap-4 sm:px-8"
              aria-label={action.label}
            >
              <action.Icon className="size-6 sm:size-7" strokeWidth={2.4} aria-hidden />
              <span>{action.label}</span>
            </button>
          ))}
        </header>

        <main className="grid items-center gap-[clamp(28px,4vw,70px)] pb-[clamp(18px,2.5vw,42px)] pt-[clamp(10px,1.8vw,28px)] lg:grid-cols-[minmax(390px,0.82fr)_minmax(650px,1.18fr)]">
          <aside className="relative mx-auto flex w-full max-w-[620px] flex-col items-center lg:mx-0">
            <div className="relative w-full max-w-[480px] pb-8 text-center">
              <p
                className="text-[clamp(64px,7vw,112px)] leading-[0.95] text-[#5b3e2b]"
                style={{ fontFamily: 'var(--font-paperlogy)' }}
              >
                플립북
              </p>
              <div className="mx-auto mt-8 inline-flex min-h-[52px] items-center rounded-[10px] bg-[#f8b5ba]/62 px-10 text-[#684834] shadow-[inset_0_-8px_0_rgb(255_255_255_/_24%)]">
                <span className="body-l-b">친구들이 모이면 바로 시작해요!</span>
              </div>
            </div>

            <section className="relative mt-[clamp(14px,3vw,42px)] w-full max-w-[560px] rounded-[34px] border border-[#e9cdb8] bg-[#fffaf3]/82 px-[clamp(26px,3vw,52px)] pb-[clamp(30px,3.4vw,56px)] pt-[clamp(58px,5.4vw,96px)] text-center shadow-[0_16px_34px_rgb(122_72_38_/_14%),inset_0_0_34px_rgb(255_244_226_/_70%)] backdrop-blur-[1px]">
              <span
                aria-hidden
                className="absolute left-1/2 top-0 h-20 w-36 -translate-x-1/2 -translate-y-[52%] rounded-[14px] bg-[#9b73df]/76 shadow-[0_8px_18px_rgb(76_45_128_/_18%),inset_0_0_0_1px_rgb(255_255_255_/_38%)]"
              />
              <span
                aria-hidden
                className="absolute left-1/2 top-0 h-36 w-16 -translate-x-1/2 -translate-y-[72%] rounded-full border-[9px] border-[#b9b4ba] shadow-[inset_0_0_0_2px_rgb(255_255_255_/_48%)]"
              />
              <span
                aria-hidden
                className="absolute left-1/2 top-0 h-28 w-24 -translate-x-1/2 -translate-y-[70%] rounded-b-[30px] border-x-[8px] border-b-[8px] border-[#c9c3c8]"
              />

              <p className="h3-b text-[#684834]">입장 코드</p>
              <p
                className="mt-5 break-all text-[clamp(52px,5.5vw,84px)] font-black leading-none text-[#684834]"
                style={{ letterSpacing: '0.04em' }}
              >
                {roomCode ?? '------'}
              </p>
              <div
                aria-hidden
                className="mx-auto mt-6 h-3 w-[min(72%,330px)] rounded-full bg-[repeating-linear-gradient(90deg,#ffd96d_0_16px,transparent_16px_24px)]"
              />
              <p className="body-l-b mt-7 text-[#b19686]">친구에게 코드를 알려주세요!</p>

              <div className="mt-9 grid grid-cols-2 gap-5">
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
            </section>
          </aside>

          <section className="relative rounded-[46px] border border-[#efd8c7] bg-white/68 p-[clamp(26px,3vw,48px)] shadow-[0_18px_44px_rgb(126_74_42_/_14%),inset_0_1px_0_rgb(255_255_255_/_86%)] backdrop-blur-sm">
            <div className="flex items-center justify-between border-b border-[#edd9c9] pb-7">
              <h2 className="h2-b inline-flex items-center gap-4 text-[#684834]">
                <UsersRound className="size-8" strokeWidth={2.2} aria-hidden />
                참여자
              </h2>
              <span className="h1-b text-[#ff7182]">
                {visibleParticipantCount} / {VISIBLE_PARTICIPANT_CAPACITY}
              </span>
            </div>

            <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2">
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
                />
              ))}
              {waitingSlots.map((waitingSlot) => (
                <WaitingParticipantSlot key={waitingSlot} />
              ))}
            </div>

            <div className="mt-8 grid gap-6 xl:grid-cols-2">
              <section className="rounded-[22px] border border-[#efd8c7] bg-white/54 p-6 shadow-[inset_0_1px_0_rgb(255_255_255_/_82%)]">
                <h3 className="h3-b inline-flex items-center gap-3 text-[#684834]">
                  <Clock3 className="size-7 text-[#ff7182]" strokeWidth={2.2} aria-hidden />
                  제한 시간
                </h3>
                <div className="mt-6 grid grid-cols-3 gap-4">
                  {FLIPBOOK_TIME_LIMITS_SECONDS.map((seconds) => (
                    <button
                      key={seconds}
                      type="button"
                      onClick={() => onSelectTimeLimit(seconds)}
                      disabled={!isHost}
                      className={cn(
                        'h3-b min-h-[72px] rounded-[16px] border border-[#f2dece] bg-[#fff2e9] text-[#b79a88] shadow-[0_7px_14px_rgb(155_93_58_/_12%)] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60',
                        selectedTimeLimitSeconds === seconds &&
                          'border-[#ff7a8c] bg-[#ff7182] text-white shadow-[0_9px_16px_rgb(255_113_130_/_28%)]',
                      )}
                    >
                      {seconds}초
                    </button>
                  ))}
                </div>
              </section>

              <section className="rounded-[22px] border border-[#efd8c7] bg-white/54 p-6 shadow-[inset_0_1px_0_rgb(255_255_255_/_82%)]">
                <h3 className="h3-b inline-flex items-center gap-3 text-[#684834]">
                  <Flag className="size-7 text-[#ff7182]" strokeWidth={2.2} aria-hidden />
                  라운드
                </h3>
                <div className="mt-7 flex items-center justify-between gap-5">
                  <span className="h3-b text-[#9a7f6d]">최소 {roundCount}</span>
                  <div className="flex items-center gap-6">
                    <button
                      type="button"
                      disabled
                      className="grid size-16 place-items-center rounded-full bg-[#fff2e9] text-[#80543b] shadow-[0_7px_14px_rgb(155_93_58_/_12%)]"
                      aria-label="라운드 감소"
                    >
                      <Minus className="size-7" strokeWidth={3} aria-hidden />
                    </button>
                    <span className="text-[48px] font-black leading-none text-[#684834]">
                      {roundCount}
                    </span>
                    <button
                      type="button"
                      disabled
                      className="grid size-16 place-items-center rounded-full bg-[#fff0ed] text-[#ff7182] shadow-[0_7px_14px_rgb(155_93_58_/_12%)]"
                      aria-label="라운드 증가"
                    >
                      <Plus className="size-8" strokeWidth={3} aria-hidden />
                    </button>
                  </div>
                </div>
              </section>
            </div>

            <button
              type="button"
              onClick={onStartGame}
              disabled={startGameButtonDisabled}
              aria-label={startGameButtonLabel}
              className="relative mx-auto mt-9 block aspect-[1125/175] w-full max-w-[760px] overflow-hidden rounded-full transition hover:-translate-y-1 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Image
                src={FLIPBOOK_LOBBY_IMAGES.startButton}
                alt=""
                fill
                sizes="(max-width: 1024px) 86vw, 760px"
                className="object-fill"
              />
              {startGameButtonLabel !== '게임 시작!' && (
                <span className="body-l-b absolute inset-0 grid place-items-center bg-[#f78b99]/72 text-white backdrop-blur-[1px]">
                  {startGameButtonLabel}
                </span>
              )}
              <span className="sr-only">{startGameButtonLabel}</span>
            </button>
            {errorMessage && (
              <p className="body-b mt-5 rounded-full bg-white/76 px-5 py-3 text-center text-[#cf5d68] shadow-[0_8px_18px_rgb(126_74_42_/_10%)]">
                {errorMessage}
              </p>
            )}
          </section>
        </main>
      </div>
    </section>
  )
}

function ParticipantNameTag({
  name,
  avatar,
  isHost,
}: {
  name: string
  avatar: string
  isHost: boolean
}) {
  return (
    <div className="flex min-h-[100px] items-center gap-5 rounded-[18px] border border-[#ffabb5] bg-[#fff1f1] px-7 shadow-[0_8px_16px_rgb(255_113_130_/_14%)]">
      <span className="grid size-16 shrink-0 place-items-center rounded-full bg-[#ffe5ad] text-[34px] shadow-[inset_0_0_0_3px_rgb(255_255_255_/_68%)]">
        {avatar}
      </span>
      <span className="h3-b min-w-0 flex-1 truncate text-[#684834]">{name}</span>
      {isHost && (
        <span className="body-b rounded-full bg-[#ff7182] px-5 py-2 text-white">방장</span>
      )}
    </div>
  )
}

function WaitingParticipantSlot() {
  return (
    <div className="flex min-h-[100px] items-center justify-center gap-8 rounded-[18px] border-2 border-dashed border-[#e7c6b6] bg-white/24 px-7 text-[#b49d91]">
      <Image
        src={FLIPBOOK_LOBBY_IMAGES.plus}
        alt=""
        width={39}
        height={39}
        className="size-10"
      />
      <span className="body-l-b">참가 기다리는 중...</span>
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
      aria-label={copyLabel}
      className="relative aspect-[154/80] min-h-[64px] overflow-hidden rounded-[18px] transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-55"
    >
      <Image
        src={
          actionKey === 'copyLink'
            ? FLIPBOOK_LOBBY_IMAGES.copyLinkButton
            : FLIPBOOK_LOBBY_IMAGES.qrCodeButton
        }
        alt=""
        fill
        sizes="160px"
        className="object-fill"
      />
      {copyLabel !== label && (
        <span className="caption-b absolute inset-0 grid place-items-center rounded-[18px] bg-white/72 text-[#684834]">
          {copyLabel}
        </span>
      )}
      <span className="sr-only">
        <Icon className="size-[17px]" aria-hidden />
        {copyLabel}
      </span>
    </button>
  )
}
