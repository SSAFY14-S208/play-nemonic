import {
  HUB_COMMUNITY_CANVAS_BOOK_POSITION,
  HUB_COMMUNITY_CANVAS_BOOK_ROTATION_Y,
} from '../constants'
import {
  preloadCommunityCanvasBookModel,
  useCommunityCanvasBookModel,
} from './useCommunityCanvasBookModel'

export default function CommunityCanvasBookMesh() {
  const model = useCommunityCanvasBookModel()

  return (
    <group
      position={HUB_COMMUNITY_CANVAS_BOOK_POSITION}
      rotation-y={HUB_COMMUNITY_CANVAS_BOOK_ROTATION_Y}
    >
      <primitive object={model} dispose={null} />
    </group>
  )
}

preloadCommunityCanvasBookModel()
