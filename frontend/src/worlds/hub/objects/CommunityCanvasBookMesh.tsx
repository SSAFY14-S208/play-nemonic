import {
  HUB_COMMUNITY_CANVAS_BOOK_POSITION,
  HUB_COMMUNITY_CANVAS_BOOK_ROTATION_Y,
} from '../constants'
import {
  useCommunityCanvasBookModel,
  useCommunityCanvasNavigation,
} from './hooks'

export default function CommunityCanvasBookMesh() {
  const model = useCommunityCanvasBookModel()
  const {
    handleCommunityCanvasClick,
    handleCommunityCanvasPointerEnter,
    handleCommunityCanvasPointerLeave,
  } = useCommunityCanvasNavigation()

  return (
    <group
      position={HUB_COMMUNITY_CANVAS_BOOK_POSITION}
      rotation-y={HUB_COMMUNITY_CANVAS_BOOK_ROTATION_Y}
      onClick={handleCommunityCanvasClick}
      onPointerEnter={handleCommunityCanvasPointerEnter}
      onPointerLeave={handleCommunityCanvasPointerLeave}
    >
      <primitive object={model} dispose={null} />
    </group>
  )
}
