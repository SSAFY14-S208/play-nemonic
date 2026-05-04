import {
  RELAY_ROUNDS,
  type RelayRoundKey,
} from '../constants'
import type { RelayRoundLines } from '../useRelayDrawing'
import { cn } from '@/shared/libs'

interface RoundProgressPanelProps {
  activeRoundKey: RelayRoundKey
  activeRoundIndex: number
  roundLines: RelayRoundLines
}

const DRAWING_PARTICIPANTS = [
  { id: 'fox', avatar: '🦊', name: '여우 (나)' },
  { id: 'cat', avatar: '🐱', name: '고양이' },
  { id: 'bear', avatar: '🐻', name: '곰돌이' },
] as const

export default function RoundProgressPanel({
  activeRoundKey,
  activeRoundIndex,
  roundLines,
}: RoundProgressPanelProps) {
  return (
    <div className="flex h-full flex-col gap-4">
      <p className="h4-b text-relay-ink">라운드 진행</p>
      <div className="relative h-[188px] shrink-0">
        {RELAY_ROUNDS.map((round, roundIndex) => {
          const isDone = roundIndex < activeRoundIndex
          const isActive = round.key === activeRoundKey
          const hasDrawing = roundLines[round.key].length > 0

          return (
            <div
              key={round.key}
              className={cn(
                'absolute left-0 flex h-[52px] w-full items-center rounded-[14px] px-5',
                isActive && 'border-2 border-relay-line bg-relay-accent text-relay-ink',
                !isActive && 'bg-relay-panel text-relay-muted',
                roundIndex > activeRoundIndex && 'opacity-60',
              )}
              style={{ top: `${roundIndex * 68}px` }}
            >
              <span
                className={cn(
                  'body-b grid size-7 place-items-center rounded-full',
                  isDone && 'bg-relay-accent-strong text-fg-inverse',
                  isActive && 'bg-relay-active text-relay-ink',
                  !isDone && !isActive && 'bg-relay-disabled text-fg-inverse',
                )}
              >
                {isDone ? '✓' : roundIndex + 1}
              </span>
              <span className="body-b flex-1 text-center">{round.label}</span>
              {isActive && <span className="size-2.5 rounded-full bg-relay-accent-strong" />}
              {!isActive && hasDrawing && <span className="size-2.5 rounded-full bg-relay-green" />}
            </div>
          )
        })}
      </div>

      <div className="h-px w-full bg-relay-accent" />
      <p className="h4-b text-relay-ink">함께하는 친구들</p>
      <div className="grid gap-4">
        {DRAWING_PARTICIPANTS.map((participant) => (
          <div
            key={participant.id}
            className="flex min-h-12 items-center gap-3 rounded-[12px] border border-relay-line bg-relay-active px-3 text-relay-accent-strong"
          >
            <span className="grid size-7 place-items-center rounded-full border border-relay-line bg-relay-active">
              {participant.avatar}
            </span>
            <span className="body-b">{participant.name}</span>
          </div>
        ))}
      </div>

      <div className="caption-r mt-auto rounded-[14px] bg-relay-accent px-4 py-3 text-relay-ink">
        <p className="caption-b">💡 팁</p>
        <p className="mt-1">
          이전 사람 그림의 하단 일부만 보여요. 마음껏 상상해서 이어 그려보세요!
        </p>
      </div>
    </div>
  )
}
