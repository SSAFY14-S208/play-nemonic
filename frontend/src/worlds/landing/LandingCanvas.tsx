'use client'
import { Canvas } from '@react-three/fiber'
import { Physics } from '@react-three/rapier'
import LandingScene from './LandingScene'

export default function LandingCanvas() {
  return (
    <Canvas
      shadows
      camera={{ fov: 45, near: 0.1, far: 500, position: [0, 12.85, 10] }}
      style={{ width: '100%', height: '100vh' }}
    >
      <Physics gravity={[0, -9.81, 0]}>
        <LandingScene />
      </Physics>
    </Canvas>
  )
}
