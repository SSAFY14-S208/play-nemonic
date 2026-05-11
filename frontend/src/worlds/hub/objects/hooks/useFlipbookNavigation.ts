import { useCallback } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { useHubViewStore } from '@/shared/stores'

const FLIPBOOK_CONTENT_KEY = 'flipbook'

function setBodyCursor(cursor: string) {
  if (typeof document === 'undefined') return
  document.body.style.cursor = cursor
}

/**
 * 플립북 mesh의 클릭/포인터 핸들러.
 * fortune 패턴과 동일 — mesh 클릭은 selectContent만, 진입은 HubOverlay 입장 버튼.
 */
export function useFlipbookNavigation() {
  const selectContent = useHubViewStore((state) => state.selectContent)

  const handleFlipbookClick = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      setBodyCursor('')
      selectContent(FLIPBOOK_CONTENT_KEY)
    },
    [selectContent],
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
