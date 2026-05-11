import {
  HUB_FLIPBOOK_BUNNY_POSITION,
  HUB_FLIPBOOK_BUNNY_ROTATION_Y,
} from '../constants'
import FlipbookRunningRabbitMesh from './FlipbookRunningRabbitMesh'
import {
  useFlipbookBunnyModel,
  useFlipbookNavigation,
} from './hooks'

export default function FlipbookBunnyMesh() {
  const model = useFlipbookBunnyModel()
  const {
    handleFlipbookClick,
    handleFlipbookPointerEnter,
    handleFlipbookPointerLeave,
  } = useFlipbookNavigation()

  return (
    <group
      position={HUB_FLIPBOOK_BUNNY_POSITION}
      rotation-y={HUB_FLIPBOOK_BUNNY_ROTATION_Y}
      onClick={handleFlipbookClick}
      onPointerEnter={handleFlipbookPointerEnter}
      onPointerLeave={handleFlipbookPointerLeave}
    >
      <primitive object={model} dispose={null} />
      <FlipbookRunningRabbitMesh />
    </group>
  )
}
