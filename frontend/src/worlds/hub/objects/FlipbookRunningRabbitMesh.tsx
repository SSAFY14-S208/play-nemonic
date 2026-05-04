import { useRef } from 'react'
import type { Group } from 'three'
import {
  preloadFlipbookRunningRabbitModel,
  useFlipbookRunningRabbitModel,
  useFlipbookRunningRabbitMotion,
} from './hooks'

export default function FlipbookRunningRabbitMesh() {
  const rabbitGroupRef = useRef<Group>(null)
  const model = useFlipbookRunningRabbitModel()

  useFlipbookRunningRabbitMotion(rabbitGroupRef)

  return (
    <group ref={rabbitGroupRef}>
      <primitive object={model} dispose={null} />
    </group>
  )
}

preloadFlipbookRunningRabbitModel()
