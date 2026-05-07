'use client'

import { Suspense, useRef } from 'react'
import { Canvas } from '@react-three/fiber'
import type { Group } from 'three'
import {
  preloadFlipbookEntranceBookModel,
  preloadFlipbookEntranceRunningRabbitModel,
  useFlipbookEntranceBookModel,
  useFlipbookEntranceRunningRabbitModel,
  useFlipbookEntranceRunningRabbitMotion,
} from './hooks'

export default function FlipbookEntranceRabbitVisual() {
  return (
    <div className="h-[520px] w-full overflow-visible">
      <Canvas
        camera={{ position: [0, 1.35, 7.6], fov: 34, near: 0.1, far: 40 }}
        gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
        dpr={[1, 2]}
      >
        <Suspense fallback={null}>
          <FlipbookEntranceRabbitScene />
        </Suspense>
      </Canvas>
    </div>
  )
}

function FlipbookEntranceRabbitScene() {
  const bookModel = useFlipbookEntranceBookModel()
  const runningRabbitModel = useFlipbookEntranceRunningRabbitModel()
  const runningRabbitGroupRef = useRef<Group>(null)

  useFlipbookEntranceRunningRabbitMotion(runningRabbitGroupRef)

  return (
    <>
      <hemisphereLight args={['#fffaf3', '#e6d8e8', 2.2]} />
      <directionalLight color="#ffffff" intensity={2.5} position={[4, 6, 5]} />
      <directionalLight color="#fff2f5" intensity={1.3} position={[-5, 3, -4]} />
      <group position={[0, -1.28, 0]} rotation-y={-0.2} scale={0.9}>
        <primitive object={bookModel} dispose={null} />
        <group ref={runningRabbitGroupRef}>
          <primitive object={runningRabbitModel} dispose={null} />
        </group>
      </group>
    </>
  )
}

preloadFlipbookEntranceBookModel()
preloadFlipbookEntranceRunningRabbitModel()
