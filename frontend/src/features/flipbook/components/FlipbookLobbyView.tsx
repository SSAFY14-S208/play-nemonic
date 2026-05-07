'use client'

import { Copy, Minus, Plus, QrCode } from 'lucide-react'
import { PostItNote } from '@/shared/components'
import {
  FLIPBOOK_PARTICIPANTS,
  FLIPBOOK_ROOM_CODE,
  FLIPBOOK_TIME_LIMITS_SECONDS,
  FLIPBOOK_TOPIC,
  type FlipbookTimeLimitSeconds,
} from '../constants'
import { cn } from '@/shared/libs'

interface FlipbookLobbyViewProps {
  selectedTimeLimitSeconds: number
  roundCount: number
  minimumRoundCount: number
  onSelectTimeLimit: (seconds: FlipbookTimeLimitSeconds) => void
  onDecreaseRoundCount: () => void
  onIncreaseRoundCount: () => void
  onStartGame: () => void
}

const WAITING_SLOT_COUNT = 1

export default function FlipbookLobbyView({
  selectedTimeLimitSeconds,
  roundCount,
  minimumRoundCount,
  onSelectTimeLimit,
  onDecreaseRoundCount,
  onIncreaseRoundCount,
  onStartGame,
}: FlipbookLobbyViewProps) {
  return (
    <section className="relative min-h-[900px] overflow-hidden border border-flipbook-light bg-flipbook-background">
      <div className="relative mx-auto h-[900px] w-full max-w-[1440px] overflow-hidden">
        <PostItNote className="absolute left-[6.8%] top-[18.1%] h-[61%] w-[39.5%] text-flipbook-primary" />

        <section className="absolute left-[9.7%] top-[32.4%] flex h-[32%] w-[33.1%] flex-col items-center justify-center gap-4 rounded-[32px] px-10 py-[60px]">
          <p className="h2-b text-fg-inverse/85">입장 코드</p>
          <p className="h1-b text-fg-inverse">
            {FLIPBOOK_ROOM_CODE}
          </p>
          <div className="mt-2 flex gap-8">
            <ShareButton label="링크 복사" Icon={Copy} />
            <ShareButton label="QR 코드" Icon={QrCode} />
          </div>
        </section>

        <div className="absolute left-[49.9%] top-[13.7%] flex h-[72%] w-[43.3%] flex-col gap-5">
          <section className="rounded-[24px] bg-flipbook-paper px-6 py-5 shadow-[0_4px_16px_10px_var(--color-flipbook-shadow)]">
            <div className="flex items-center gap-1">
              <h2 className="h3-b text-flipbook-ink">참여자</h2>
              <span className="h3-b text-flipbook-primary">
                {FLIPBOOK_PARTICIPANTS.length} / 12
              </span>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3">
              {FLIPBOOK_PARTICIPANTS.map((participant) => (
                <div
                  key={participant.id}
                  className="flex min-h-14 items-center rounded-[16px] border border-flipbook-light bg-flipbook-light px-5"
                >
                  <span className="body-b flex-1 text-flipbook-ink">{participant.name}</span>
                </div>
              ))}
              {Array.from({ length: WAITING_SLOT_COUNT }).map((unusedSlot, waitingSlotIndex) => (
                <div
                  key={`${unusedSlot}-${waitingSlotIndex}`}
                  className="caption-b grid min-h-14 place-items-center rounded-[14px] border border-dashed border-flipbook-primary text-flipbook-muted"
                >
                  초대를 기다리는 중...
                </div>
              ))}
            </div>
          </section>

          <section className="rounded-[24px] bg-flipbook-paper px-8 py-5 shadow-[0_4px_16px_10px_var(--color-flipbook-shadow)]">
            <h2 className="h3-b text-flipbook-muted">주제</h2>
            <p className="h4-b mt-2 text-flipbook-ink">{FLIPBOOK_TOPIC}</p>
          </section>

          <section className="rounded-[24px] bg-flipbook-paper px-8 py-5 shadow-[0_4px_16px_10px_var(--color-flipbook-shadow)]">
            <h2 className="h3-b text-flipbook-muted">⏱ 제한 시간</h2>
            <div className="mt-5 grid grid-cols-3 gap-3">
              {FLIPBOOK_TIME_LIMITS_SECONDS.map((seconds) => (
                <button
                  key={seconds}
                  type="button"
                  onClick={() => onSelectTimeLimit(seconds)}
                  className={cn(
                    'body-b min-h-12 rounded-[12px] border border-flipbook-light bg-flipbook-light text-flipbook-primary',
                    selectedTimeLimitSeconds === seconds && 'text-flipbook-ink',
                  )}
                >
                  {seconds}초
                </button>
              ))}
            </div>
          </section>

          <section className="flex items-center justify-between rounded-[24px] bg-flipbook-paper px-8 py-5 shadow-[0_4px_16px_10px_var(--color-flipbook-shadow)]">
            <div>
              <h2 className="h3-b text-flipbook-muted">라운드 수</h2>
              <p className="caption-r mt-1 text-flipbook-muted">최소 {minimumRoundCount}라운드</p>
            </div>
            <div className="flex items-center gap-3">
              <button
                type="button"
                aria-label="라운드 줄이기"
                onClick={onDecreaseRoundCount}
                className="grid size-10 place-items-center rounded-full bg-flipbook-light text-flipbook-ink"
              >
                <Minus className="size-4" aria-hidden />
              </button>
              <span className="h2-b min-w-8 text-center text-flipbook-ink">{roundCount}</span>
              <button
                type="button"
                aria-label="라운드 늘리기"
                onClick={onIncreaseRoundCount}
                className="grid size-10 place-items-center rounded-full bg-flipbook-primary text-flipbook-ink"
              >
                <Plus className="size-4" aria-hidden />
              </button>
            </div>
          </section>

          <button
            type="button"
            onClick={onStartGame}
            className="body-b min-h-16 rounded-[16px] bg-flipbook-primary text-flipbook-ink shadow-[0_6px_16px_var(--color-flipbook-shadow)]"
          >
            🎨 게임 시작
          </button>
        </div>
      </div>
    </section>
  )
}

function ShareButton({
  label,
  Icon,
}: {
  label: string
  Icon: typeof Copy
}) {
  return (
    <button
      type="button"
      className="body-b inline-flex min-h-[45px] items-center gap-1.5 rounded-full border border-flipbook-primary bg-flipbook-light px-4 text-flipbook-deep"
    >
      <Icon className="size-[17px]" aria-hidden />
      {label}
    </button>
  )
}
