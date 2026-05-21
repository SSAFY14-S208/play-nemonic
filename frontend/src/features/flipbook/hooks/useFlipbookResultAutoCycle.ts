'use client'

import { useEffect, useRef } from 'react'

// GIF가 보이기 시작한 시점으로부터 다음 작품으로 넘기기 전 머무는 시간 (ms).
// FlipbookPrintResultStage의 onParticipantRevealComplete 콜백 발사 시점을
// 기준으로 한다.
const FLIPBOOK_RESULT_HOLD_AFTER_REVEAL_MS = 5000

interface UseFlipbookResultAutoCycleParams {
  // 결과 화면이 활성화된 동안에만 자동 전환을 동작시키기 위한 가드. false면 no-op.
  enabled: boolean
  resultCount: number
  activeResultIndex: number
  // FlipbookPrintResultStage의 onParticipantRevealComplete가 마지막으로 신호한
  // 참여자 인덱스. 이 값이 activeResultIndex와 일치할 때(=현재 활성 작품의 reveal
  // 시퀀스가 끝났을 때)에만 5초 타이머를 시작한다. 일치하지 않는 동안(reveal
  // 진행 중)에는 타이머가 걸리지 않아 mid-reveal 컷이 발생하지 않는다.
  revealedResultIndex: number | null
  // useFlipbook.selectResult — 인덱스 업데이트와 함께 resultFrameIndex 리셋 +
  // GIF 재생 재시작을 수행한다.
  onSelectResult: (resultIndex: number) => void
}

// 플립북 결과 화면에서 작품 자동 전환. 작품마다 print/reveal 시퀀스가 끝나고
// GIF가 화면에 보인 시점부터 FLIPBOOK_RESULT_HOLD_AFTER_REVEAL_MS만큼 머무른 뒤
// 다음 작품으로 넘긴다. (작품마다 프레임 수가 달라 print 시간이 가변적이라
// 고정 시간 대신 reveal 완료 신호를 기준으로 카운트한다.)
//
// 마지막 작품에서는 첫 작품으로 순환. 작품이 1개 이하이면 비활성.
export function useFlipbookResultAutoCycle({
  enabled,
  resultCount,
  activeResultIndex,
  revealedResultIndex,
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
    // 현재 활성 작품의 reveal이 아직 끝나지 않았으면 대기.
    if (revealedResultIndex !== activeResultIndex) return

    const timer = window.setTimeout(() => {
      onSelectResultRef.current((activeResultIndex + 1) % resultCount)
    }, FLIPBOOK_RESULT_HOLD_AFTER_REVEAL_MS)
    return () => window.clearTimeout(timer)
  }, [enabled, resultCount, activeResultIndex, revealedResultIndex])
}
