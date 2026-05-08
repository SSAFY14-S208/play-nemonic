import type { RelayResultReveal, RelayResultSegment } from '../../constants'
import { cn } from '@/shared/libs'

interface ResultCreditsPanelProps {
  activeReveal: RelayResultReveal
  activeRevealIndex: number
  isFinalReveal: boolean
  segments: RelayResultSegment[]
  participantCount: number
  ownerNickname: string
  ownerAvatar: string
}

// 우측 사이드 — 작성자 크레딧. 단계 진행에 따라 active/complete 마크가 바뀐다.
export default function ResultCreditsPanel({
  activeReveal,
  activeRevealIndex,
  isFinalReveal,
  segments,
  participantCount,
  ownerNickname,
  ownerAvatar,
}: ResultCreditsPanelProps) {
  return (
    <section className="rounded-[18px] border border-relay-line bg-relay-paper px-5 py-4">
      <p className="caption-b text-relay-accent-strong">
        이번엔 {participantCount}명이 모였어요
      </p>
      <h2 className="h4-b mt-2 text-relay-ink">
        {ownerAvatar} {ownerNickname} 님의 캐릭터
      </h2>

      <div className="mt-4 grid gap-2">
        {segments.map((segment, segmentIndex) => {
          const isActive = !isFinalReveal && segment.roleLabel === activeReveal.roleLabel
          const isComplete = isFinalReveal || segmentIndex < activeRevealIndex

          return (
            <div
              key={segment.key}
              className={cn(
                'flex min-h-11 items-center gap-3 rounded-[14px] bg-relay-credit-row px-3.5',
                isActive &&
                  'border-[1.5px] border-relay-accent-strong bg-relay-paper shadow-[0_4px_5px_rgba(212,155,31,0.18)]',
              )}
            >
              <span className="text-[18px]">{segment.avatar}</span>
              <span className="body-b text-relay-ink">{segment.participantName}</span>
              <span className="flex-1" />
              <span
                className={cn(
                  'caption-b rounded-full border border-relay-line bg-relay-paper px-2.5 py-1 text-relay-accent-strong',
                  isActive && 'border-relay-accent-strong bg-relay-active',
                )}
              >
                {segment.roleLabel}
              </span>
              {isComplete && (
                <span className="caption-b grid size-[18px] place-items-center rounded-full bg-relay-accent-strong text-fg-inverse">
                  ✓
                </span>
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}
