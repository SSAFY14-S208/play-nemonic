'use client'

import { useEffect, useRef } from 'react'

// 결과 화면에서 작품 자동 전환 간격 (ms). 릴레이 드로잉과 동일하게 7초.
const FLIPBOOK_RESULT_AUTO_ADVANCE_MS = 7000

interface UseFlipbookResultAutoCycleParams {
  // 결과 화면이 활성화된 동안에만 자동 전환을 동작시키기 위한 가드. false면 no-op.
  enabled: boolean
  resultCount: number
  activeResultIndex: number
  // useFlipbook.selectResult — 인덱스 업데이트와 함께 resultFrameIndex 리셋 +
  // GIF 재생 재시작을 수행한다. 자동 전환에서도 수동 선택과 동일한 UX를 위해
  // 직접 setActiveResultIndex가 아닌 이 selector를 사용한다.
  onSelectResult: (resultIndex: number) => void
}

// 플립북 결과 화면에서 FLIPBOOK_RESULT_AUTO_ADVANCE_MS 간격으로 다음 작품으로
// 자동 전환한다. 마지막 작품에서는 첫 작품으로 순환해 슬라이드쇼처럼 계속 돈다.
// 작품이 1개 이하이면 자동 전환을 비활성화한다.
//
// activeResultIndex가 바뀔 때마다(자동/수동 모두) effect가 재실행되어 타이머가
// 리셋되므로, 사용자가 작품 선택 버튼으로 직접 다른 작품을 선택해도 그 시점부터
// 다시 7초 카운트가 시작된다.
export function useFlipbookResultAutoCycle({
  enabled,
  resultCount,
  activeResultIndex,
  onSelectResult,
}: UseFlipbookResultAutoCycleParams) {
  // useFlipbook의 selectResult는 내부 의존성(resultPlayback) 때문에 매 렌더마다
  // 새 reference로 만들어진다. effect deps에 그대로 포함하면 매 렌더마다
  // 타이머가 리셋되어 발사되지 못하므로, ref로 최신 값을 추적해 deps에서 제외한다.
  const onSelectResultRef = useRef(onSelectResult)
  useEffect(() => {
    onSelectResultRef.current = onSelectResult
  }, [onSelectResult])

  useEffect(() => {
    if (!enabled) return
    if (resultCount <= 1) return
    const timer = window.setTimeout(() => {
      onSelectResultRef.current((activeResultIndex + 1) % resultCount)
    }, FLIPBOOK_RESULT_AUTO_ADVANCE_MS)
    return () => window.clearTimeout(timer)
  }, [enabled, resultCount, activeResultIndex])
}
