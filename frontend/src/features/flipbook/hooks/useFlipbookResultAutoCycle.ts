'use client'

import { useEffect, useRef } from 'react'

import { FLIPBOOK_RESULT_FRAME_DURATION_MS } from './useFlipbookResultPlayback'

// 한 작품의 모든 프레임을 다 보여준 뒤 마지막 프레임에서 정지 상태로 머무르는
// 시간 (ms). 다 재생되고 5초 동안 결과를 감상한 뒤 다음 작품으로 넘어간다.
const FLIPBOOK_RESULT_HOLD_AFTER_PLAY_MS = 5000

interface UseFlipbookResultAutoCycleParams {
  // 결과 화면이 활성화된 동안에만 자동 전환을 동작시키기 위한 가드. false면 no-op.
  enabled: boolean
  resultCount: number
  activeResultIndex: number
  // 현재 활성 작품의 프레임 수. 작품마다 다르므로 자동 전환 시간도 동적으로 결정.
  // 0 이하이면 결과가 아직 로딩 중이므로 자동 전환을 시도하지 않는다.
  currentFrameCount: number
  // useFlipbook.selectResult — 인덱스 업데이트와 함께 resultFrameIndex 리셋 +
  // GIF 재생 재시작을 수행한다. 자동 전환에서도 수동 선택과 동일한 UX를 위해
  // 직접 setActiveResultIndex가 아닌 이 selector를 사용한다.
  onSelectResult: (resultIndex: number) => void
}

// 플립북 결과 화면에서 작품을 자동 전환한다. 작품당 시간 =
//   프레임수 × FLIPBOOK_RESULT_FRAME_DURATION_MS + FLIPBOOK_RESULT_HOLD_AFTER_PLAY_MS
// 즉 한 작품의 GIF를 처음부터 끝까지 한 번 재생한 뒤 마지막 프레임에서 5초간
// 머무른 다음 다음 작품으로 넘어가도록 한다. (useFlipbookResultPlayback이
// 마지막 프레임 도달 시 루프를 중단하므로 mid-loop 컷이 발생하지 않는다.)
//
// 마지막 작품에서는 첫 작품으로 순환. 작품이 1개 이하 또는 프레임이 아직 비어있
// 으면 자동 전환을 비활성화한다.
//
// activeResultIndex나 프레임 수가 바뀔 때마다(자동/수동 모두) effect가 재실행되어
// 타이머가 그 시점부터 다시 카운트된다.
export function useFlipbookResultAutoCycle({
  enabled,
  resultCount,
  activeResultIndex,
  currentFrameCount,
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
    if (currentFrameCount <= 0) return

    const delayMs =
      currentFrameCount * FLIPBOOK_RESULT_FRAME_DURATION_MS +
      FLIPBOOK_RESULT_HOLD_AFTER_PLAY_MS

    const timer = window.setTimeout(() => {
      onSelectResultRef.current((activeResultIndex + 1) % resultCount)
    }, delayMs)
    return () => window.clearTimeout(timer)
  }, [enabled, resultCount, activeResultIndex, currentFrameCount])
}
