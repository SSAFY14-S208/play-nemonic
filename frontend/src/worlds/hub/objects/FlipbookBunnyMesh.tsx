import {
  HUB_FLIPBOOK_BUNNY_POSITION,
  HUB_FLIPBOOK_BUNNY_ROTATION_Y,
} from '../constants'
import FlipbookRunningRabbitMesh from './FlipbookRunningRabbitMesh'
import { useFlipbookBunnyModel } from './hooks'

export default function FlipbookBunnyMesh() {
  const model = useFlipbookBunnyModel()

  return (
    <group
      position={HUB_FLIPBOOK_BUNNY_POSITION}
      rotation-y={HUB_FLIPBOOK_BUNNY_ROTATION_Y}
    >
      <primitive object={model} dispose={null} />
      <FlipbookRunningRabbitMesh />
    </group>
  )
}
