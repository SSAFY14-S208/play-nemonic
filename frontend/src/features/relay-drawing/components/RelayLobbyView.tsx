import {
  RELAY_ACTIONS,
  RELAY_EMPTY_SLOTS,
  RELAY_PARTICIPANTS,
  RELAY_ROOM_CODE,
  RELAY_TIME_LIMITS_SECONDS,
} from '../constants'

interface RelayLobbyViewProps {
  onStartGame: () => void
}

export default function RelayLobbyView({ onStartGame }: RelayLobbyViewProps) {
  return (
    <section className="mx-auto grid min-h-[calc(100svh-72px)] w-full max-w-[1280px] items-start gap-16 px-6 py-14 lg:grid-cols-[0.95fr_1.05fr] lg:py-20">
      <div className="grid min-h-[460px] place-items-center rounded-[2rem] border border-relay-border bg-relay-background">
        <div className="text-center">
          <p className="body-m text-relay-ink">방 코드</p>
          <p
            className="h1-b mt-8 text-relay-ink"
            style={{ fontSize: 'clamp(4.25rem, 8vw, 6rem)', lineHeight: 1 }}
          >
            {RELAY_ROOM_CODE}
          </p>
          <div className="mt-8 flex justify-center gap-3">
            {RELAY_ACTIONS.map(({ label, Icon }) => (
              <button
                key={label}
                type="button"
                className="caption-b inline-flex min-h-9 items-center gap-2 rounded-full bg-relay-panel px-4 text-relay-ink"
              >
                <Icon className="size-4 text-relay-muted" aria-hidden />
                {label}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="grid gap-5">
        <section className="rounded-[2rem] bg-relay-paper p-8 shadow-sm">
          <div className="flex items-center gap-1">
            <h2 className="h4-b text-relay-ink">참여자</h2>
            <span className="body-b text-relay-accent-strong">3 / 6</span>
          </div>

          <div className="mt-5 grid gap-3 sm:grid-cols-2">
            {RELAY_PARTICIPANTS.map((participant) => {
              const ParticipantIcon = participant.Icon

              return (
                <div
                  key={participant.id}
                  className="flex min-h-16 items-center justify-between rounded-[var(--radius-lg)] border border-relay-line bg-relay-active px-5"
                >
                  <div className="flex items-center gap-3">
                    <span className="grid size-9 place-items-center rounded-full bg-relay-panel text-relay-accent-strong">
                      <ParticipantIcon className="size-5" aria-hidden />
                    </span>
                    <span className="body-b text-relay-ink">{participant.name}</span>
                  </div>
                  <span className="caption-b rounded-full bg-relay-panel px-3 py-1 text-relay-muted">
                    {participant.role}
                  </span>
                </div>
              )
            })}

            {RELAY_EMPTY_SLOTS.map((slotLabel, index) => (
              <div
                key={`${slotLabel}-${index}`}
                className="caption-m grid min-h-16 place-items-center rounded-[var(--radius-lg)] border border-dashed border-relay-line text-relay-muted"
              >
                {slotLabel}
              </div>
            ))}
          </div>
        </section>

        <section className="rounded-[var(--radius-xl)] border border-relay-border bg-relay-background p-6">
          <p className="body-b text-relay-muted">⏱ 제한 시간</p>
          <div className="mt-4 grid grid-cols-3 gap-3">
            {RELAY_TIME_LIMITS_SECONDS.map((seconds) => (
              <button
                key={seconds}
                type="button"
                className="body-b min-h-12 rounded-[var(--radius-md)] border border-relay-border bg-relay-panel text-relay-ink transition-colors hover:bg-relay-active"
              >
                {seconds}초
              </button>
            ))}
          </div>
        </section>

        <button
          type="button"
          onClick={onStartGame}
          className="body-b min-h-16 rounded-[var(--radius-xl)] border border-relay-accent-strong bg-relay-accent text-relay-ink shadow-[0_14px_24px_rgba(255,184,46,0.28)] transition-transform hover:-translate-y-0.5"
        >
          🎨 게임 시작 (3명)
        </button>
      </div>
    </section>
  )
}
