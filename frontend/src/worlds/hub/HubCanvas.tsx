'use client'
import { Canvas } from '@react-three/fiber'
import { HUB_CAMERA_FAR, HUB_CAMERA_FOV } from './constants'
import HubScene from './HubScene'
import { useHubCanvasLifecycle } from './hooks'

export default function HubCanvas() {
  const { handleCanvasCreated } = useHubCanvasLifecycle()

  return (
    <Canvas
      camera={{ position: [0, 2.65, 19.2], fov: HUB_CAMERA_FOV, near: 0.1, far: HUB_CAMERA_FAR }}
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
      onCreated={handleCanvasCreated}
      style={{
        position: 'absolute',
        inset: 0,
        width: '100%',
        height: '100%',
        zIndex: 1,
        cursor: 'grab',
      }}
      dpr={[1, 2]}
    >
      <HubScene />
    </Canvas>
  )
}
