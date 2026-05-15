import { useCallback } from 'react'
import type { ThreeEvent } from '@react-three/fiber'
import { useHubRoomStore } from '@/shared/stores'

function setDocumentCursor(cursor: string) {
  if (typeof document === 'undefined') return
  document.body.style.cursor = cursor
}

export function usePegboardArea() {
  const setFocus = useHubRoomStore((state) => state.setFocus)

  const focusWorkspace = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      setFocus('workspace')
    },
    [setFocus],
  )

  const focusPegboard = useCallback(
    (event: ThreeEvent<MouseEvent>) => {
      event.stopPropagation()
      setFocus('pegboard')
    },
    [setFocus],
  )

  const handlePointerEnter = useCallback(() => {
    setDocumentCursor('pointer')
  }, [])

  const handlePointerLeave = useCallback(() => {
    setDocumentCursor('')
  }, [])

  return {
    focusPegboard,
    focusWorkspace,
    handlePointerEnter,
    handlePointerLeave,
  }
}
