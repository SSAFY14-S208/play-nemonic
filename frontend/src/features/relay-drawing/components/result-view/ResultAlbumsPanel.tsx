import { cn } from '@/shared/libs'

import { RESULT_SEGMENTS } from './resultSegments'

// 우측 사이드 — 다른 참가자들의 앨범 미리보기 그리드.
export default function ResultAlbumsPanel() {
  return (
    <section className="rounded-[18px] border border-relay-line bg-relay-paper p-5">
      <p className="caption-b text-relay-accent-strong">앨범 둘러보기</p>
      <div className="mt-3 grid grid-cols-3 gap-2">
        {RESULT_SEGMENTS.map((segment, index) => (
          <button
            key={segment.key}
            type="button"
            className={cn(
              'caption-b grid min-h-[88px] place-items-center rounded-[12px] border-[1.5px] border-relay-line bg-relay-credit-row text-relay-accent-strong',
              index === 0 && 'border-relay-accent-strong bg-relay-active',
            )}
          >
            <span className="grid justify-items-center gap-1">
              <span className="text-[22px]">{segment.avatar}</span>
              <span>{segment.participantName.replace(' (나)', '')}</span>
            </span>
          </button>
        ))}
      </div>
    </section>
  )
}
