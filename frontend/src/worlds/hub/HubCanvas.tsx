'use client'
import { Canvas } from '@react-three/fiber'
import HubScene from './HubScene'

export default function HubCanvas() {
  return (
    <Canvas
      camera={{ position: [0, 2.65, 19.2], fov: 28, near: 0.1, far: 100 }}
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
      onCreated={({ camera, gl }) => {
        camera.lookAt(0, 0.05, 0)
        gl.domElement.addEventListener('webglcontextlost', (event) => {
          event.preventDefault()
        })
      }}
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
