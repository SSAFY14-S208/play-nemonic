import { useCallback } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { useRouter } from 'next/navigation'
import { useHubViewStore } from '@/shared/stores'
import { HUB_COMMUNITY_CANVAS_PATH } from '../../constants'

const COMMUNITY_CONTENT_KEY = 'community'

function setBodyCursor(cursor: string) {
  if (typeof document === 'undefined') return
  document.body.style.cursor = cursor
}

/**
 * 커뮤니티 캔버스 mesh의 클릭/포인터 핸들러.
 * - 헤더가 이 콘텐츠로 떠있을 때(selectedContentKey === 'community')만 라우팅
 * - 그 외에는 광장이 회전해서 헤더가 community로 매칭될 때까지 selectContent만 호출
 *   (mesh가 정면에 오면 syncActiveContentByAngle이 자동으로 selectedContentKey를 설정)
 */
export function useCommunityCanvasNavigation() {
  const router = useRouter()
  const selectedContentKey = useHubViewStore((state) => state.selectedContentKey)
  const selectContent = useHubViewStore((state) => state.selectContent)

  const handleCommunityCanvasClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      setBodyCursor('')
      if (selectedContentKey === COMMUNITY_CONTENT_KEY) {
        router.push(HUB_COMMUNITY_CANVAS_PATH)
        return
      }
      selectContent(COMMUNITY_CONTENT_KEY)
    },
    [router, selectContent, selectedContentKey],
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
