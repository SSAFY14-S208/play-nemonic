import { RELAY_PARTICIPANTS } from '../constants'
import { cn } from '@/shared/libs'

interface RelayArtworkCardProps {
  character?: 'left' | 'right' | 'center'
  size?: 'hero' | 'result'
}

export default function RelayArtworkCard({
  character = 'center',
  size = 'hero',
}: RelayArtworkCardProps) {
  const isResult = size === 'result'

  return (
    <figure
      className="relative overflow-hidden rounded-[var(--radius-md)] bg-relay-paper shadow-[0_20px_45px_rgba(148,124,64,0.16)]"
      aria-label="완성된 릴레이 캐릭터"
    >
      <div className={cn(isResult ? 'aspect-[4/3] p-7' : 'aspect-[2/3] p-6')}>
        <div className="relative h-full w-full">
          <RelayDashedGuide top="35%" />
          <RelayDashedGuide top="66%" />

          {character === 'left' && <LeftCharacter />}
          {character === 'right' && <RightCharacter />}
          {character === 'center' && <CenterCharacter />}

          {isResult && (
            <div className="absolute left-4 top-4 flex flex-col gap-[34%]">
              {RELAY_PARTICIPANTS.map((participant) => (
                <span
                  key={participant.id}
                  className="caption-b rounded-full bg-relay-active px-3 py-1 text-relay-muted"
                >
                  {participant.avatar} · {participant.role}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>
    </figure>
  )
}

function RelayDashedGuide({ top }: { top: string }) {
  return (
    <div
      className="absolute left-0 right-0 h-px opacity-50"
      style={{
        top,
        backgroundImage:
          'linear-gradient(to right, var(--color-relay-dash) 0 8px, transparent 8px 18px)',
      }}
    />
  )
}

function LeftCharacter() {
  return (
    <>
      <div className="absolute left-1/2 top-[4%] h-[30%] w-[50%] -translate-x-1/2 rounded-full border-[4px] border-relay-coral bg-relay-paper">
        <span className="absolute left-[32%] top-[34%] size-2 rounded-full bg-relay-ink" />
        <span className="absolute right-[31%] top-[39%] size-3 rounded-full bg-relay-ink" />
        <span className="absolute bottom-[18%] left-1/2 h-4 w-12 -translate-x-1/2 rotate-[12deg] rounded-full border-[3px] border-relay-ink" />
      </div>

      <div className="absolute left-1/2 top-[41%] h-[26%] w-[34%] -translate-x-1/2 rotate-[8deg] rounded-[var(--radius-sm)] border-[4px] border-relay-card-blue bg-relay-paper">
        {[28, 50, 72].map((verticalPosition) => (
          <span
            key={verticalPosition}
            className="absolute left-1/2 size-2 -translate-x-1/2 rounded-full bg-relay-card-blue"
            style={{ top: `${verticalPosition}%` }}
          />
        ))}
      </div>

      <span className="absolute bottom-[11%] left-[37%] h-[20%] w-2 rotate-[8deg] rounded-full bg-relay-green" />
      <span className="absolute bottom-[10%] right-[37%] h-[18%] w-2 rotate-[8deg] rounded-full bg-relay-green" />
      <span className="absolute bottom-[8%] left-[34%] h-3 w-8 rounded-full bg-relay-ink" />
      <span className="absolute bottom-[7%] right-[31%] h-3 w-8 rounded-full bg-relay-ink" />
    </>
  )
}

function RightCharacter() {
  return (
    <>
      <div className="absolute left-1/2 top-[3%] h-[31%] w-[56%] -translate-x-1/2 rounded-full border-[4px] border-relay-ink bg-relay-skin">
        <span className="absolute -top-[9%] left-[-6%] h-[34%] w-[104%] -rotate-[5deg] rounded-full bg-relay-yellow" />
        <span className="absolute left-[30%] top-[33%] size-2 rounded-full bg-relay-ink" />
        <span className="absolute right-[30%] top-[33%] size-2 rounded-full bg-relay-ink" />
        <span className="absolute bottom-[21%] left-1/2 h-4 w-8 -translate-x-1/2 rounded-full border-[3px] border-relay-ink bg-relay-skin" />
      </div>

      <div className="absolute left-1/2 top-[42%] h-[23%] w-[58%] -translate-x-1/2 rounded-[var(--radius-md)] bg-relay-card-blue">
        <span className="absolute -left-[16%] top-[15%] h-[76%] w-[20%] rounded-[var(--radius-sm)] bg-relay-card-blue" />
        <span className="absolute -right-[16%] top-[15%] h-[76%] w-[20%] rounded-[var(--radius-sm)] bg-relay-card-blue" />
      </div>

      <span className="absolute bottom-[8%] left-[34%] h-[28%] w-[20%] rotate-[-4deg] rounded-[var(--radius-sm)] bg-relay-ink" />
      <span className="absolute bottom-[8%] right-[34%] h-[28%] w-[20%] rotate-[-4deg] rounded-[var(--radius-sm)] bg-relay-ink" />
    </>
  )
}

function CenterCharacter() {
  return (
    <>
      <div className="absolute left-1/2 top-[4%] h-[31%] w-[58%] -translate-x-1/2 rounded-full border-[4px] border-relay-ink bg-relay-yellow">
        <span className="body-b absolute left-[30%] top-[26%] text-relay-ink">X</span>
        <span className="body-b absolute right-[30%] top-[26%] text-relay-ink">X</span>
        <span className="absolute bottom-[20%] left-1/2 h-[19%] w-[45%] -translate-x-1/2 rounded-full border-[4px] border-relay-ink" />
      </div>

      <div
        className="absolute left-1/2 top-[43%] h-[27%] w-[55%] -translate-x-1/2 bg-relay-triangle"
        style={{ clipPath: 'polygon(50% 0, 100% 92%, 0 84%)' }}
      />
      <div
        className="absolute left-1/2 top-[44%] h-[24%] w-[50%] -translate-x-1/2 bg-relay-pink"
        style={{ clipPath: 'polygon(50% 0, 100% 92%, 0 84%)' }}
      />

      <span className="absolute bottom-[5%] left-[45%] h-[27%] w-[5px] rotate-[3deg] bg-relay-ink" />
      <span className="absolute bottom-[5%] right-[45%] h-[27%] w-[5px] rotate-[3deg] bg-relay-ink" />
    </>
  )
}
