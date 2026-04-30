import { ArrowRight, Sparkles } from 'lucide-react'
import RelayArtworkCard from './RelayArtworkCard'

interface RelayBoothViewProps {
  onCreateRoom: () => void
  onEnterRoom: () => void
}

export default function RelayBoothView({ onCreateRoom, onEnterRoom }: RelayBoothViewProps) {
  return (
    <section className="mx-auto grid min-h-[calc(100svh-72px)] w-full max-w-[1280px] items-center gap-10 px-6 py-12 lg:grid-cols-[0.95fr_1.25fr]">
      <div className="border border-relay-border bg-relay-background/40 p-6 md:p-10">
        <div className="caption-m inline-flex items-center gap-2 rounded-full border border-relay-border bg-relay-panel px-3 py-1 text-relay-muted">
          <Sparkles className="size-4 text-relay-accent-strong" aria-hidden />
          2~6명 · 우당탕 릴레이 드로잉
        </div>
        <h1
          className="h1-b mt-9 max-w-[620px] text-relay-ink"
          style={{ fontSize: 'clamp(2.5rem, 5vw, 4.5rem)', lineHeight: 1.08 }}
        >
          우당탕 릴레이 드로잉
        </h1>
        <p className="body-l-r mt-6 max-w-[520px] text-relay-muted">
          얼굴 → 몸통 → 다리, 3라운드. 캔버스가 다음 사람에게 넘어가요.
          이전 사람 그림의 하단 일부 힌트만 보고 이어 그리면 예상 밖 캐릭터가 탄생합니다.
        </p>
        <div className="mt-9 flex flex-wrap gap-3">
          <button
            type="button"
            onClick={onCreateRoom}
            className="body-b inline-flex min-h-14 items-center gap-2 rounded-[var(--radius-xl)] border border-relay-accent-strong bg-relay-accent px-7 text-relay-ink shadow-[0_12px_22px_rgba(255,184,46,0.28)] transition-transform hover:-translate-y-0.5"
          >
            방 만들기
            <ArrowRight className="size-4" aria-hidden />
          </button>
          <button
            type="button"
            onClick={onEnterRoom}
            className="body-b min-h-14 rounded-[var(--radius-xl)] border border-relay-accent-strong bg-relay-paper px-7 text-relay-accent-strong transition-colors hover:bg-relay-panel"
          >
            방 입장
          </button>
        </div>
      </div>

      <div className="relative mx-auto h-[580px] w-full max-w-[680px] overflow-hidden rounded-[var(--radius-md)] bg-relay-pink shadow-[0_16px_32px_rgba(184,121,22,0.12)]">
        <div className="absolute left-8 top-8 h-11 w-16 rotate-[18deg] rounded-[var(--radius-sm)] bg-relay-paper" />
        <div className="absolute right-8 top-16 h-10 w-14 rotate-[-18deg] rounded-[var(--radius-sm)] bg-relay-paper" />
        <div className="absolute bottom-14 left-12 h-12 w-12 rotate-[-18deg] rounded-[var(--radius-sm)] bg-relay-paper" />
        <div className="absolute bottom-9 right-20 h-10 w-12 rotate-[8deg] rounded-[var(--radius-sm)] bg-relay-paper" />
        <div className="absolute right-[10%] top-[40%]" style={{ fontSize: '3rem' }} aria-hidden>
          ✏️
        </div>

        <div className="absolute left-[6%] top-[15%] w-[35%] rotate-[10deg]">
          <div className="aspect-[2/3]">
            <RelayArtworkCard character="left" />
          </div>
        </div>
        <div className="absolute right-[8%] top-[7%] w-[35%] rotate-[-8deg]">
          <div className="aspect-[2/3]">
            <RelayArtworkCard character="right" />
          </div>
        </div>
        <div className="absolute left-1/2 top-[35%] z-10 w-[38%] -translate-x-1/2 rotate-[3deg]">
          <div className="aspect-[2/3]">
            <RelayArtworkCard character="center" />
          </div>
        </div>
      </div>
    </section>
  )
}
