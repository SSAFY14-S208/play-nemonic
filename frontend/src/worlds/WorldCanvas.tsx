'use client'
import { Canvas } from '@react-three/fiber'
import { Physics } from '@react-three/rapier'
import SceneManager from './_infra/SceneManager'

export default function WorldCanvas() {
  return (
    <Canvas>
      <Physics gravity={[0, -9.81, 0]}>
        <SceneManager />
      </Physics>
    </Canvas>
  )
}
