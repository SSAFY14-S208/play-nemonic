import { useCallback } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { HUB_RELAY_DRAWING_URL } from '../../constants'

function setBodyCursor(cursor: string) {
  if (typeof document === 'undefined') return
  document.body.style.cursor = cursor
}

export function useRelayDrawingNavigation() {
  const handleRelayDrawingClick = useCallback((event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation()
    window.location.assign(HUB_RELAY_DRAWING_URL)
  }, [])

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
