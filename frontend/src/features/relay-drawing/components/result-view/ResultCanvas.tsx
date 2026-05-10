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
        'relative min-h-0 flex-1 overflow-hidden rounded-[14px] border-[1.5px] border-relay-line bg-relay-background',
        // 모바일에선 부모 높이가 정해지지 않아 flex-1만으론 사이즈가 안 잡히므로
        // 명시적 min-height로 보장. 데스크탑(lg+)에선 viewport가 짧을 때 min-h가
        // 거꾸로 부모를 밀어내 StepNav가 카드 밖으로 빠지므로 lg:min-h-0으로 풀어둔다.
        isFinalReveal ? 'min-h-110 lg:min-h-0' : 'min-h-100 lg:min-h-0',
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
