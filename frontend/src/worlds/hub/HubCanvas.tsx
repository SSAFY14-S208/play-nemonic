'use client'
import { Canvas } from '@react-three/fiber'
import { cn } from '@/shared/libs'
import { useHubViewStore } from '@/shared/stores'
import { HUB_CAMERA_FAR, HUB_CAMERA_FOV } from './constants'
import HubScene from './HubScene'
import { useHubCanvasLifecycle } from './hooks'

export default function HubCanvas() {
  const { handleCanvasCreated } = useHubCanvasLifecycle()
  const isDragging = useHubViewStore((state) => state.isDragging)

  return (
    <Canvas
      camera={{ position: [0, 2.65, 13.9], fov: HUB_CAMERA_FOV, near: 0.1, far: HUB_CAMERA_FAR }}
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
      onCreated={handleCanvasCreated}
      className={cn(
        'absolute inset-0 z-[1] h-full w-full',
        isDragging ? 'cursor-grabbing' : 'cursor-grab',
      )}
      dpr={[1, 2]}
    >
      <HubScene />
    </Canvas>
  )
}
