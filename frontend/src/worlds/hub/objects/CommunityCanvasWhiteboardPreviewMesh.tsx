import * as THREE from 'three'
import {
  useCommunityCanvasWhiteboardPreviewMemos,
  useCommunityCanvasWhiteboardPreviewTexture,
} from './hooks'

const WHITEBOARD_PREVIEW_POSITION: [number, number, number] = [
  -0.638,
  0.562,
  -0.1087,
]
const WHITEBOARD_PREVIEW_QUATERNION: [number, number, number, number] = [
  -0.00585,
  -0.70849,
  0.00587,
  -0.70568,
]
const WHITEBOARD_PREVIEW_PLANE_SIZE: [number, number] = [0.43, 0.265]
const WHITEBOARD_PREVIEW_SURFACE_OFFSET = -0.004
const DISABLED_RAYCAST: THREE.Mesh['raycast'] = () => undefined

interface CommunityCanvasWhiteboardPreviewMeshProps {
  flipContentX?: boolean
  isExpanded: boolean
  position?: [number, number, number]
  quaternion?: [number, number, number, number]
  scale?: number
}

export default function CommunityCanvasWhiteboardPreviewMesh({
  flipContentX = false,
  isExpanded,
  position = WHITEBOARD_PREVIEW_POSITION,
  quaternion = WHITEBOARD_PREVIEW_QUATERNION,
  scale = 1,
}: CommunityCanvasWhiteboardPreviewMeshProps) {
  const { previewMemos } = useCommunityCanvasWhiteboardPreviewMemos()
  const { canvas, textureRef } = useCommunityCanvasWhiteboardPreviewTexture({
    isExpanded,
    previewMemos,
  })

  if (!canvas) return null

  return (
    <group
      position={position}
      quaternion={quaternion}
      scale={scale}
    >
      <mesh
        position={[0, 0, WHITEBOARD_PREVIEW_SURFACE_OFFSET]}
        raycast={DISABLED_RAYCAST}
        renderOrder={80}
        scale={[flipContentX ? -1 : 1, 1, 1]}
      >
        <planeGeometry args={WHITEBOARD_PREVIEW_PLANE_SIZE} />
        <meshBasicMaterial
          alphaTest={0.02}
          depthTest={false}
          depthWrite={false}
          forceSinglePass
          opacity={isExpanded ? 1 : 0.9}
          side={THREE.DoubleSide}
          toneMapped={false}
          transparent
        >
          <canvasTexture
            ref={textureRef}
            attach="map"
            colorSpace={THREE.SRGBColorSpace}
            generateMipmaps={false}
            image={canvas}
            magFilter={THREE.LinearFilter}
            minFilter={THREE.LinearFilter}
            wrapS={THREE.ClampToEdgeWrapping}
            wrapT={THREE.ClampToEdgeWrapping}
          />
        </meshBasicMaterial>
      </mesh>
    </group>
  )
}
