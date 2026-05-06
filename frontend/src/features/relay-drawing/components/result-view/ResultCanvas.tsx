import {
  RELAY_ROUND_ORDER,
  type RelayResultRevealStep,
  type RelayRoundKey,
} from '../../constants'
import type { RelayRoundLines } from '../../types'
import { cn } from '@/shared/libs'

import CompositeDrawingCanvas from './CompositeDrawingCanvas'
import ResultSegmentTags from './ResultSegmentTags'
import ResultSpotlight from './ResultSpotlight'

interface ResultCanvasProps {
  resultRevealStep: RelayResultRevealStep
  roundLines: RelayRoundLines
}

// 결과 캔버스 박스 — 단계에 따라 한 라운드만 보이거나 최종 3개 합성을 보여준다.
// 컨텍스트별 부가 UI(스포트라이트/세그먼트 태그)도 여기서 결합.
export default function ResultCanvas({ resultRevealStep, roundLines }: ResultCanvasProps) {
  const isFinalReveal = resultRevealStep === 'final'
  const visibleRoundKeys =
    resultRevealStep === 'final'
      ? RELAY_ROUND_ORDER
      : [resultRevealStep as RelayRoundKey]

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
      />

      {!isFinalReveal && <ResultSpotlight revealStep={resultRevealStep} />}

      {isFinalReveal && <ResultSegmentTags />}
    </div>
  )
}
