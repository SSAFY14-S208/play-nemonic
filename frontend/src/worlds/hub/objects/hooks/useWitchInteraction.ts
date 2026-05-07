import { useCallback } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { useRouter } from 'next/navigation'
import { useHubViewStore } from '@/shared/stores'
import { HUB_FORTUNE_PATH } from '../../constants'

function setBodyCursor(cursor: string) {
  if (typeof document === 'undefined') {
    return
  }

  document.body.style.cursor = cursor
}

export function useWitchInteraction() {
  const router = useRouter()
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
    selectContent('fortune')
    setBodyCursor('')
    router.push(HUB_FORTUNE_PATH)
  }, [router, selectContent])

  return {
    handleWitchClick,
    handleWitchPointerEnter,
    handleWitchPointerLeave,
  }
}
