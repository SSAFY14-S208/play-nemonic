import {
  RELAY_FINAL_STAGE_SIZE,
  RELAY_ROUND_ORDER,
  RELAY_STAGE_SIZE,
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
//
// reveal 모드별로 자연스러운 비율을 유지한다:
//   • per-part(848:720): 가로형. 한 파트가 박스에 꽉 차게 보임.
//   • final  (848:1920): 세로형. 3 파트가 자연 비례로 보임.
// 모바일에선 컬럼 전체 폭을 채우고(`w-full + aspect-ratio`로 높이 자동 도출),
// 데스크탑(lg+)에선 높이를 viewport에 맞춰 cap하고 너비는 aspect-ratio에서
// 자동 도출되어 컬럼 안 중앙 정렬된다. height cap이 진입하면서 페이지 전체가
// viewport 안에 들어가 스크롤이 생기지 않는다.
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
        // 모바일: 컬럼 폭 full(높이는 aspect-ratio에서 도출).
        'w-full',
        // 데스크탑(lg+): 너비 auto + 높이 cap. mx-auto로 컬럼 안 중앙 정렬.
        'lg:mx-auto lg:w-auto lg:max-w-full lg:self-center',
        isFinalReveal
          ? 'lg:h-[min(64vh,760px)]'
          : 'lg:h-[min(54vh,600px)]',
      )}
      style={{
        aspectRatio: isFinalReveal
          ? `${RELAY_STAGE_SIZE.width} / ${RELAY_FINAL_STAGE_SIZE.height}`
          : `${RELAY_STAGE_SIZE.width} / ${RELAY_STAGE_SIZE.height}`,
      }}
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
