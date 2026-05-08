import type { RelayResultReveal } from '../../constants'

interface ResultStageHeaderProps {
  activeReveal: RelayResultReveal
}

// 결과 stage 위쪽 — 단계 번호 / 작성자 닉네임 / 이번 단계 라벨.
export default function ResultStageHeader({ activeReveal }: ResultStageHeaderProps) {
  return (
    <header className="mb-4 flex min-h-[60px] items-end justify-between gap-4">
      <div>
        <p className="caption-b text-relay-accent-strong">
          STEP {activeReveal.order} · {activeReveal.roleLabel}
        </p>
        <h1 className="h2-b mt-1 flex flex-wrap items-center gap-2 text-relay-ink">
          <span className="rounded-full bg-relay-active px-3 py-0.5">
            {activeReveal.avatar} {activeReveal.participantName}
          </span>
          <span>{activeReveal.titleSuffix}</span>
        </h1>
      </div>

      <span className="caption-b rounded-full border-[1.5px] border-relay-line bg-relay-paper px-3 py-1 text-relay-accent-strong">
        {activeReveal.avatar} {activeReveal.roleLabel}
      </span>
    </header>
  )
}
