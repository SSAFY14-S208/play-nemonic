import {
  RELAY_RESULT_REVEALS,
  type RelayResultRevealStep,
} from '../../constants'

interface ResultSpotlightProps {
  revealStep: RelayResultRevealStep
}

// 캔버스 우상단 spotlight — "방금 그린 사람" 라벨.
export default function ResultSpotlight({ revealStep }: ResultSpotlightProps) {
  const activeReveal =
    RELAY_RESULT_REVEALS.find((reveal) => reveal.key === revealStep) ?? RELAY_RESULT_REVEALS[0]

  return (
    <div className="absolute right-4 top-4 flex items-center gap-2 rounded-full border-[1.5px] border-relay-line bg-relay-paper py-1.5 pl-2 pr-4 shadow-[0_6px_7px_rgba(212,156,31,0.18)]">
      <span className="grid size-8 place-items-center rounded-full bg-relay-active">
        {activeReveal.avatar}
      </span>
      <span>
        <span className="caption-b block text-relay-accent-strong">
          {activeReveal.spotlightLabel}
        </span>
        <span className="caption-b block text-relay-ink">{activeReveal.participantName}</span>
      </span>
    </div>
  )
}
