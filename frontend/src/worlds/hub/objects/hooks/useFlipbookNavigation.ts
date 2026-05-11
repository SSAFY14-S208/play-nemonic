import { useCallback } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { useRouter } from 'next/navigation'
import { useHubViewStore } from '@/shared/stores'
import { HUB_FLIPBOOK_PATH } from '../../constants'

const FLIPBOOK_CONTENT_KEY = 'flipbook'

function setBodyCursor(cursor: string) {
  if (typeof document === 'undefined') return
  document.body.style.cursor = cursor
}

/**
 * 플립북 mesh의 클릭/포인터 핸들러.
 * - 헤더가 flipbook으로 떠있을 때만 /flipbook 으로 라우팅
 * - 그 외에는 selectContent만 호출 (mesh가 정면으로 회전할 동안 헤더가 매칭됨)
 */
export function useFlipbookNavigation() {
  const router = useRouter()
  const selectedContentKey = useHubViewStore((state) => state.selectedContentKey)
  const selectContent = useHubViewStore((state) => state.selectContent)

  const handleFlipbookClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      setBodyCursor('')
      if (selectedContentKey === FLIPBOOK_CONTENT_KEY) {
        router.push(HUB_FLIPBOOK_PATH)
        return
      }
      selectContent(FLIPBOOK_CONTENT_KEY)
    },
    [router, selectContent, selectedContentKey],
  )

  const handleFlipbookPointerEnter = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setBodyCursor('pointer')
    },
    [],
  )

  const handleFlipbookPointerLeave = useCallback(
    (event: ThreeEvent<PointerEvent>) => {
      event.stopPropagation()
      setBodyCursor('')
    },
    [],
  )

  return {
    handleFlipbookClick,
    handleFlipbookPointerEnter,
    handleFlipbookPointerLeave,
  }
}
