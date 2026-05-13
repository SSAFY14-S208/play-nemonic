import { useCallback } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { useHubViewStore } from '@/shared/stores'

const COMMUNITY_CONTENT_KEY = 'community'

function setBodyCursor(cursor: string) {
  if (typeof document === 'undefined') return
  document.body.style.cursor = cursor
}

/**
 * 커뮤니티 캔버스 mesh의 클릭/포인터 핸들러.
 * fortune 패턴과 동일 — mesh 클릭은 selectContent만 호출해 광장을 그쪽으로 회전시키고
 * 헤더만 띄운다. 실제 페이지 진입은 HubOverlay의 입장 버튼이 담당한다.
 */
export function useCommunityCanvasNavigation() {
  const selectContent = useHubViewStore((state) => state.selectContent)

  const handleCommunityCanvasClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      setBodyCursor('')
      selectContent(COMMUNITY_CONTENT_KEY)
    },
    [selectContent],
  )

  const handleCommunityCanvasPointerEnter = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setBodyCursor('pointer')
    },
    [],
  )

  const handleCommunityCanvasPointerLeave = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setBodyCursor('')
    },
    [],
  )

  return {
    handleCommunityCanvasClick,
    handleCommunityCanvasPointerEnter,
    handleCommunityCanvasPointerLeave,
  }
}
