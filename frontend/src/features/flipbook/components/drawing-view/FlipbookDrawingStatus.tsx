'use client'

import { Timer } from 'lucide-react'
import { cn } from '@/shared/libs'

export function TopStatusBar({
  activeRoundIndex,
  roundCount,
  remainingSeconds,
  instructionText,
}: {
  activeRoundIndex: number
  roundCount: number
  remainingSeconds: number
  instructionText: string
}) {
  return (
    <header className="absolute left-[96px] top-[56px] h-[108px] w-[1344px] rounded-full border border-[#ead7c9] bg-white shadow-[0_12px_34px_rgb(129_89_54_/_14%)]">
      <div className="flex h-full items-center px-[86px]">
        <p className="text-[74px] font-bold leading-none text-[#f45d8d]">
          {activeRoundIndex + 1}/{roundCount}
        </p>
        <div className="mx-10 h-14 w-px bg-[#ead7c9]" />
        <div>
          <p className="body-l-b text-[26px] text-[#30343b]">{instructionText}</p>
        </div>
        <div className="ml-auto inline-flex h-16 min-w-[184px] items-center justify-center gap-3 rounded-full border border-[#ead7c9] bg-white px-6 text-[#f45d8d] shadow-[0_7px_16px_rgb(129_89_54_/_13%)]">
          <Timer className="size-10" aria-hidden />
          <span className="text-[34px] font-bold leading-none">{remainingSeconds}</span>
          <span className="body-l-b">초</span>
        </div>
      </div>
    </header>
  )
}

export function ProgressRail({
  activeRoundIndex,
  roundCount,
}: {
  activeRoundIndex: number
  roundCount: number
}) {
  const progressDotCount = Math.max(roundCount, 1)

  return (
    <div className="absolute left-[546px] top-[928px] h-[72px] w-[444px] rounded-full border border-[#ead7c9] bg-white shadow-[0_10px_22px_rgb(125_84_50_/_13%)]">
      <div className="absolute left-[64px] right-[64px] top-1/2 h-[3px] -translate-y-1/2 bg-[#ded3ca]" />
      {Array.from({ length: progressDotCount }).map((unusedValue, progressIndex) => (
        <span
          key={`${unusedValue}-${progressIndex}`}
          className={cn(
            'absolute top-1/2 grid size-6 -translate-y-1/2 place-items-center rounded-full border-[3px] border-[#cfc5bd] bg-white',
            progressIndex <= activeRoundIndex && 'border-[#f45d8d] bg-[#f45d8d]',
            progressIndex === activeRoundIndex && 'ring-[6px] ring-white',
          )}
          style={{ left: `${64 + progressIndex * (316 / Math.max(progressDotCount - 1, 1))}px` }}
        />
      ))}
    </div>
  )
}
