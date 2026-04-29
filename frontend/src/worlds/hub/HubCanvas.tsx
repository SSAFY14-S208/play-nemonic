'use client'
import { Canvas } from '@react-three/fiber'
import { Physics } from '@react-three/rapier'
import HubScene from './HubScene'

export default function HubCanvas() {
  return (
    <Canvas>
      <Physics gravity={[0, -9.81, 0]}>
        <HubScene />
      </Physics>
    </Canvas>
  )
}
