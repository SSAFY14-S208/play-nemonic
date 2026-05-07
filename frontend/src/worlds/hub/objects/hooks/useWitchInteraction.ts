import { useCallback } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { type HubContentKey, useHubViewStore } from '@/shared/stores'

function setBodyCursor(cursor: string) {
  if (typeof document === 'undefined') {
    return
  }

  document.body.style.cursor = cursor
}

export function useWitchInteraction(contentKey: HubContentKey) {
  const setWitchHovered = useHubViewStore((state) => state.setWitchHovered)
  const selectContent = useHubViewStore((state) => state.selectContent)

  const handleWitchPointerEnter = useCallback((event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation()
    setWitchHovered(true)
    setBodyCursor('pointer')
  }, [setWitchHovered])

  const handleWitchPointerLeave = useCallback((event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation()
    setWitchHovered(false)
    setBodyCursor('')
  }, [setWitchHovered])

  const handleWitchClick = useCallback((event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation()
    selectContent(contentKey)
    setBodyCursor('')
  }, [contentKey, selectContent])

  return {
    handleWitchClick,
    handleWitchPointerEnter,
    handleWitchPointerLeave,
  }
}
