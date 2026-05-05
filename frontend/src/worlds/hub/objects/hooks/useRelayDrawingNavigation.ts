import { useCallback } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { useRouter } from 'next/navigation'
import { HUB_RELAY_DRAWING_PATH } from '../../constants'

function setBodyCursor(cursor: string) {
  if (typeof document === 'undefined') return
  document.body.style.cursor = cursor
}

export function useRelayDrawingNavigation() {
  const router = useRouter()

  const handleRelayDrawingClick = useCallback((event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation()
    setBodyCursor('')
    router.push(HUB_RELAY_DRAWING_PATH)
  }, [router])

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
