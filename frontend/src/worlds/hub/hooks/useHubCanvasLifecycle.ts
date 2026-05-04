import { useCallback } from 'react'
import type { RootState } from '@react-three/fiber'

export function useHubCanvasLifecycle() {
  const handleCanvasCreated = useCallback(({ camera }: RootState) => {
    camera.lookAt(0, 0.05, 0)
  }, [])

  return { handleCanvasCreated }
}
