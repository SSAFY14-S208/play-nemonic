import { useCallback } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { type HubContentKey, useHubViewStore } from '@/shared/stores'

export function useWitchInteraction(contentKey: HubContentKey) {
  const setWitchHovered = useHubViewStore((state) => state.setWitchHovered)
  const selectContent = useHubViewStore((state) => state.selectContent)

  const handleWitchPointerEnter = useCallback((event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation()
    setWitchHovered(true)
  }, [setWitchHovered])

  const handleWitchPointerLeave = useCallback((event: ThreeEvent<PointerEvent>) => {
    event.stopPropagation()
    setWitchHovered(false)
  }, [setWitchHovered])

  const handleWitchClick = useCallback((event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation()
    selectContent(contentKey)
  }, [contentKey, selectContent])

  return {
    handleWitchClick,
    handleWitchPointerEnter,
    handleWitchPointerLeave,
  }
}
