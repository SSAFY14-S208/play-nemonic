import { useCallback, useEffect, useState } from 'react'
import type { RootState } from '@react-three/fiber'

export function useHubCanvasLifecycle() {
  const [canvasElement, setCanvasElement] = useState<HTMLCanvasElement | null>(null)

  const handleCanvasCreated = useCallback(({ camera, gl }: RootState) => {
    camera.lookAt(0, 0.05, 0)
    setCanvasElement(gl.domElement)
  }, [])

  useEffect(() => {
    if (!canvasElement) return undefined

    const handleContextLost = (event: Event) => {
      // Prevent the browser's default permanent context loss handling.
      event.preventDefault()
    }

    canvasElement.addEventListener('webglcontextlost', handleContextLost, false)

    return () => {
      canvasElement.removeEventListener('webglcontextlost', handleContextLost, false)
    }
  }, [canvasElement])

  return { handleCanvasCreated }
}
