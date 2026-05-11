import { useCallback } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { useHubViewStore } from '@/shared/stores'

const RELAY_CONTENT_KEY = 'relay'

function setBodyCursor(cursor: string) {
  if (typeof document === 'undefined') return
  document.body.style.cursor = cursor
}

/**
 * 우당탕 릴레이 드로잉 placeholder mesh의 클릭/포인터 핸들러.
 * fortune·community·flipbook과 같은 패턴 — mesh 클릭은 selectContent만,
 * 진입은 HubOverlay의 입장 버튼이 담당한다.
 */
export function useRelayDrawingNavigation() {
  const selectContent = useHubViewStore((state) => state.selectContent)

  const handleRelayDrawingClick = useCallback((event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation()
    setBodyCursor('')
    selectContent(RELAY_CONTENT_KEY)
  }, [selectContent])

  const handleRelayDrawingPointerEnter = useCallback((event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation()
    setBodyCursor('pointer')
  }, [])

  const handleRelayDrawingPointerLeave = useCallback((event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation()
    setBodyCursor('')
  }, [])

  return {
    handleRelayDrawingClick,
    handleRelayDrawingPointerEnter,
    handleRelayDrawingPointerLeave,
  }
}
