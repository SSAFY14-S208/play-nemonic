'use client'

import dynamic from 'next/dynamic'
import { DrawingSessionControls } from '@/shared/components'
import {
  RELAY_ROUND_ORDER,
  RELAY_ROUND_SEGMENTS,
  type RelayRoundKey,
  type RelayToolKey,
} from '../constants'
import type { RelayDrawLine, RelayRoundLines } from '../useRelayDrawing'
import DrawingToolPanel from './DrawingToolPanel'
import RoundProgressPanel from './RoundProgressPanel'
import type { KonvaEventObject } from 'konva/lib/Node'

const RelayDrawingStage = dynamic(() => import('../RelayDrawingStage'), {
  ssr: false,
})

interface RelayDrawingViewProps {
  activeRoundKey: RelayRoundKey
  activeRoundIndex: number
  remainingSeconds: number
  selectedToolKey: RelayToolKey
  selectedColor: string
  strokeWidth: number
  lines: RelayDrawLine[]
  previousRoundLines: RelayDrawLine[]
  roundLines: RelayRoundLines
  onSelectTool: (tool: RelayToolKey) => void
  onSelectColor: (color: string) => void
  onStrokeWidthChange: (strokeWidth: number) => void
  onUndoDrawing: () => void
  onClearDrawing: () => void
  onDrawStart: (event: KonvaEventObject<MouseEvent | TouchEvent>) => void
  onDrawMove: (event: KonvaEventObject<MouseEvent | TouchEvent>) => void
  onDrawEnd: () => void
  onExit: () => void
  onCompleteRound: () => void
}

export default function RelayDrawingView({
  activeRoundKey,
  activeRoundIndex,
  remainingSeconds,
  selectedToolKey,
  selectedColor,
  strokeWidth,
  lines,
  previousRoundLines,
  roundLines,
  onSelectTool,
  onSelectColor,
  onStrokeWidthChange,
  onUndoDrawing,
  onClearDrawing,
  onDrawStart,
  onDrawMove,
  onDrawEnd,
  onExit,
  onCompleteRound,
}: RelayDrawingViewProps) {
  const activeRound = RELAY_ROUND_SEGMENTS[activeRoundKey]
  const isLastRound = activeRoundIndex === RELAY_ROUND_ORDER.length - 1

  return (
    <section className="relative min-h-[900px] overflow-hidden bg-relay-background text-relay-ink">
      <div className="relative mx-auto h-[900px] w-full max-w-[1440px] overflow-hidden">
        <DrawingSessionControls
          tone="relay"
          remainingSeconds={remainingSeconds}
          onExit={onExit}
        />

        <aside className="absolute left-[33px] top-[184px] flex h-[481px] w-[225px] flex-col justify-center gap-4 rounded-[24px] bg-relay-paper p-6 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
          <DrawingToolPanel
            selectedToolKey={selectedToolKey}
            selectedColor={selectedColor}
            strokeWidth={strokeWidth}
            onSelectTool={onSelectTool}
            onSelectColor={onSelectColor}
            onStrokeWidthChange={onStrokeWidthChange}
            onUndoDrawing={onUndoDrawing}
            onClearDrawing={onClearDrawing}
          />
        </aside>

        <main className="absolute left-[273px] top-[90px] h-[720px] w-[848px] overflow-hidden rounded-[16px] bg-relay-paper shadow-[0_28px_60px_rgba(148,124,64,0.12)]">
          <RelayDrawingStage
            activeRoundKey={activeRoundKey}
            lines={lines}
            previousRoundLines={previousRoundLines}
            onDrawStart={onDrawStart}
            onDrawMove={onDrawMove}
            onDrawEnd={onDrawEnd}
          />
        </main>

        <aside className="absolute left-[1136px] top-[143px] h-[625px] w-[270px] overflow-hidden rounded-[24px] bg-relay-paper p-6 shadow-[0_4px_16px_10px_rgba(184,121,22,0.1)]">
          <RoundProgressPanel
            activeRoundKey={activeRoundKey}
            activeRoundIndex={activeRoundIndex}
            roundLines={roundLines}
          />
        </aside>

        <button
          type="button"
          onClick={onCompleteRound}
          className="body-b absolute left-[1136px] top-[792px] min-h-14 w-[270px] rounded-[16px] bg-relay-accent text-relay-ink shadow-[0_6px_16px_rgba(184,121,22,0.35)]"
        >
          {isLastRound ? '결과 합치기 →' : `${activeRound.label} 저장하고 다음 →`}
        </button>
      </div>
    </section>
  )
}
