import {
  RELAY_ROUND_ORDER,
  type RelayResultReveal,
  type RelayResultSegment,
  type RelayRoundKey,
} from '../../constants'
import type { RelayRoundLines } from '../../types'
import { cn } from '@/shared/libs'

import CompositeDrawingCanvas from './CompositeDrawingCanvas'
import ResultSegmentTags from './ResultSegmentTags'
import ResultSpotlight from './ResultSpotlight'

interface ResultCanvasProps {
  activeReveal: RelayResultReveal
  roundLines: RelayRoundLines
  resultImageUrl: string | null
  segments: RelayResultSegment[]
}

// 결과 캔버스 박스 — 단계에 따라 한 라운드만 보이거나 최종 3개 합성을 보여준다.
// 컨텍스트별 부가 UI(스포트라이트/세그먼트 태그)도 여기서 결합.
export default function ResultCanvas({
  activeReveal,
  roundLines,
  resultImageUrl,
  segments,
}: ResultCanvasProps) {
  const isFinalReveal = activeReveal.key === 'final'
  const visibleRoundKeys =
    activeReveal.key === 'final'
      ? RELAY_ROUND_ORDER
      : [activeReveal.key as RelayRoundKey]

  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-[14px] border-[1.5px] border-relay-line bg-relay-background',
        isFinalReveal ? 'h-[495px]' : 'h-[460px]',
      )}
    >
      <CompositeDrawingCanvas
        visibleRoundKeys={visibleRoundKeys}
        roundLines={roundLines}
        isFinalReveal={isFinalReveal}
        resultImageUrl={resultImageUrl}
      />

      {!isFinalReveal && <ResultSpotlight activeReveal={activeReveal} />}

      {isFinalReveal && <ResultSegmentTags segments={segments} />}
    </div>
  )
}
