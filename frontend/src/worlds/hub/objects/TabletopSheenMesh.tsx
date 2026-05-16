import * as THREE from 'three'
import type { HubPerformanceMode } from '@/shared/types'

const DISABLED_RAYCAST: THREE.Mesh['raycast'] = () => undefined

const TABLETOP_SHEEN_PROFILES: Record<
  HubPerformanceMode,
  {
    opacity: number
  }
> = {
  diagnostic: {
    opacity: 0,
  },
  balanced: {
    opacity: 0.015,
  },
  quality: {
    opacity: 0.02,
  },
}

const TABLETOP_SHEEN_SURFACES = [
  {
    name: 'main-tabletop-sheen',
    position: [-0.141, 0.315, -0.577] as [number, number, number],
    size: [0.82, 0.28] as [number, number],
  },
  {
    name: 'left-tabletop-sheen',
    position: [-0.485, 0.315, 0.077] as [number, number, number],
    size: [0.28, 1.22] as [number, number],
  },
  {
    name: 'right-tabletop-sheen',
    position: [0.484, 0.315, -0.256] as [number, number, number],
    size: [0.28, 1.22] as [number, number],
  },
]

export default function TabletopSheenMesh({
  performanceMode,
}: {
  performanceMode: HubPerformanceMode
}) {
  const { opacity } = TABLETOP_SHEEN_PROFILES[performanceMode]

  if (opacity <= 0) return null

  return (
    <>
      {TABLETOP_SHEEN_SURFACES.map((surface) => (
        <mesh
          key={surface.name}
          position={surface.position}
          raycast={DISABLED_RAYCAST}
          renderOrder={24}
          rotation={[-Math.PI / 2, 0, 0]}
        >
          <planeGeometry args={surface.size} />
          <meshBasicMaterial
            blending={THREE.AdditiveBlending}
            color="#fff8ff"
            depthWrite={false}
            opacity={opacity}
            polygonOffset
            polygonOffsetFactor={-2}
            polygonOffsetUnits={-2}
            side={THREE.DoubleSide}
            toneMapped
            transparent
          />
        </mesh>
      ))}
    </>
  )
}
