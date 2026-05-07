import {
  HUB_RELAY_DRAWING_PLACEHOLDER_CUBES,
  HUB_RELAY_DRAWING_PLACEHOLDER_POSITION,
  HUB_RELAY_DRAWING_PLACEHOLDER_ROTATION_Y,
} from '../constants'
import { useRelayDrawingNavigation } from './hooks'

export default function RelayDrawingPlaceholderMesh() {
  const {
    handleRelayDrawingClick,
    handleRelayDrawingPointerEnter,
    handleRelayDrawingPointerLeave,
  } = useRelayDrawingNavigation()

  return (
    <group
      position={HUB_RELAY_DRAWING_PLACEHOLDER_POSITION}
      rotation-y={HUB_RELAY_DRAWING_PLACEHOLDER_ROTATION_Y}
      onClick={handleRelayDrawingClick}
      onPointerEnter={handleRelayDrawingPointerEnter}
      onPointerLeave={handleRelayDrawingPointerLeave}
    >
      {HUB_RELAY_DRAWING_PLACEHOLDER_CUBES.map((cube) => (
        <mesh
          key={cube.name}
          name={cube.name}
          position={cube.position}
          scale={cube.scale}
        >
          <boxGeometry args={[1, 1, 1]} />
          <meshStandardMaterial
            color={cube.color}
            emissive={cube.color}
            emissiveIntensity={0.12}
            roughness={0.88}
            metalness={0}
          />
        </mesh>
      ))}
    </group>
  )
}
