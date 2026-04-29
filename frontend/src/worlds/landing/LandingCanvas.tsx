'use client'
import { Canvas } from '@react-three/fiber'
import { Physics } from '@react-three/rapier'
import LandingScene from './LandingScene'

export default function LandingCanvas() {
  return (
    <Canvas>
      <Physics gravity={[0, -9.81, 0]}>
        <LandingScene />
      </Physics>
    </Canvas>
  )
}
