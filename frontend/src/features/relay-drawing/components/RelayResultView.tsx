import { Sparkles } from 'lucide-react'
import { RELAY_PARTICIPANTS, RELAY_RESULT_ACTIONS } from '../constants'
import RelayArtworkCard from './RelayArtworkCard'
import { cn } from '@/shared/libs'

interface RelayResultViewProps {
  onCreateAnother: () => void
}

export default function RelayResultView({ onCreateAnother }: RelayResultViewProps) {
  return (
    <section className="mx-auto grid min-h-[calc(100svh-72px)] w-full max-w-[1180px] items-center gap-10 px-6 py-12 lg:grid-cols-[1.1fr_0.9fr]">
      <div className="relative rounded-[var(--radius-md)] bg-relay-result-panel p-8 shadow-[0_26px_48px_rgba(148,124,64,0.14)]">
        <p className="caption-b text-relay-ink">2026.04.28 · 🎨🐱🧸 3명이 합작</p>
        <h1 className="h2-b mt-4 text-relay-ink">엇... 어색한데?</h1>
        <div className="mt-6">
          <RelayArtworkCard size="result" />
        </div>
        <p className="body-b mt-5 text-center text-relay-accent-strong">
          각자 그린 부분이 합쳐졌어요
        </p>
        <div className="absolute -right-8 -top-10 grid size-20 place-items-center rounded-full bg-relay-yellow text-fg-inverse shadow-[0_18px_28px_rgba(255,184,46,0.25)]">
          <Sparkles className="size-9" aria-hidden />
        </div>
      </div>

      <div className="grid gap-6">
        <section className="rounded-[var(--radius-xl)] border border-relay-border bg-relay-background p-8">
          <p className="caption-b text-relay-line">TEAMMATES</p>
          <h2 className="h3-b mt-3 text-relay-ink">이번엔 3명이 모였어요</h2>
          <div className="mt-7 grid gap-4">
            {RELAY_PARTICIPANTS.map((participant) => {
              const ParticipantIcon = participant.Icon

              return (
                <div
                  key={participant.id}
                  className="flex min-h-14 items-center justify-between rounded-[var(--radius-lg)] border border-relay-border bg-relay-panel px-4"
                >
                  <div className="flex items-center gap-3">
                    <span className="grid size-9 place-items-center rounded-full bg-relay-active text-relay-accent-strong">
                      <ParticipantIcon className="size-5" aria-hidden />
                    </span>
                    <span className="body-b text-relay-ink">{participant.name}</span>
                  </div>
                  <span className="caption-b rounded-full bg-relay-paper px-3 py-1 text-relay-muted">
                    {participant.role}
                  </span>
                </div>
              )
            })}
          </div>
        </section>

        <section className="rounded-[var(--radius-xl)] bg-relay-paper p-8">
          <p className="caption-b text-relay-line">OTHER CHARACTERS</p>
          <h2 className="h4-b mt-3 text-relay-ink">캐릭터 1 / 3</h2>
          <div className="mt-6 flex gap-3">
            {RELAY_PARTICIPANTS.map((participant, index) => (
              <div key={participant.id} className="grid justify-items-center gap-2">
                <span className="grid h-16 w-8 place-items-center rounded-full bg-relay-accent text-relay-ink">
                  {participant.avatar}
                </span>
                <span className="caption-b text-relay-muted">{index + 1}/3</span>
              </div>
            ))}
          </div>
        </section>

        <div className="grid gap-3 sm:grid-cols-2">
          {RELAY_RESULT_ACTIONS.map(({ label, Icon }, index) => (
            <button
              key={label}
              type="button"
              className={cn(
                'body-b inline-flex min-h-14 items-center justify-center gap-2 rounded-[var(--radius-lg)] border border-relay-accent-strong',
                index === 0 && 'bg-relay-active text-relay-muted',
                index !== 0 && 'bg-relay-accent text-relay-ink',
              )}
            >
              <Icon className="size-4" aria-hidden />
              {label}
            </button>
          ))}
        </div>

        <button
          type="button"
          onClick={onCreateAnother}
          className="caption-b justify-self-center text-relay-muted"
        >
          🎨 새 릴레이 만들기
        </button>
      </div>
    </section>
  )
}
