import { useEffect, useMemo, useRef } from 'react'
import * as THREE from 'three'
import type { HubPerformanceMode } from '@/shared/types'

const CARPET_FUR_PROFILES: Record<
  HubPerformanceMode,
  {
    fiberCount: number
    fiberHeight: number
    fiberRadius: number
  }
> = {
  diagnostic: {
    fiberCount: 320,
    fiberHeight: 0.009,
    fiberRadius: 0.00055,
  },
  balanced: {
    fiberCount: 7600,
    fiberHeight: 0.017,
    fiberRadius: 0.0005,
  },
  quality: {
    fiberCount: 9200,
    fiberHeight: 0.019,
    fiberRadius: 0.00058,
  },
}

const CARPET_POSITION: [number, number, number] = [
  0.1325927826360724,
  0.037,
  -0.004382307219849141,
]

const CARPET_ROTATION: [number, number, number] = [0, Math.PI * 0.5, 0]

const CARPET_SIZE = {
  depth: 0.95,
  width: 1.42,
}

const DISABLED_RAYCAST: THREE.Mesh['raycast'] = () => undefined

function seededRandom(seed: number) {
  const sineValue = Math.sin(seed * 12.9898) * 43758.5453

  return sineValue - Math.floor(sineValue)
}

export default function CarpetFurMesh({
  performanceMode,
}: {
  performanceMode: HubPerformanceMode
}) {
  const carpetFurProfile = CARPET_FUR_PROFILES[performanceMode]
  const instanceCount = carpetFurProfile.fiberCount
  const matrices = useMemo(() => {
    const dummyObject = new THREE.Object3D()
    const nextMatrices: THREE.Matrix4[] = []

    for (let fiberIndex = 0; fiberIndex < instanceCount; fiberIndex += 1) {
      const randomX = seededRandom(fiberIndex + 1)
      const randomZ = seededRandom(fiberIndex + 101)
      const randomTilt = seededRandom(fiberIndex + 201)
      const randomHeight = seededRandom(fiberIndex + 301)
      const fiberScale = 0.58 + randomHeight * 0.5

      dummyObject.position.set(
        (randomX - 0.5) * CARPET_SIZE.width,
        carpetFurProfile.fiberHeight * fiberScale * 0.5,
        (randomZ - 0.5) * CARPET_SIZE.depth,
      )
      dummyObject.rotation.set(
        (randomTilt - 0.5) * 0.72,
        seededRandom(fiberIndex + 401) * Math.PI,
        (seededRandom(fiberIndex + 501) - 0.5) * 0.72,
      )
      dummyObject.scale.setScalar(fiberScale)
      dummyObject.updateMatrix()
      nextMatrices.push(dummyObject.matrix.clone())
    }

    return nextMatrices
  }, [carpetFurProfile.fiberHeight, instanceCount])

  const instancedMeshRef = useRef<THREE.InstancedMesh>(null)

  useEffect(() => {
    const instancedMesh = instancedMeshRef.current
    if (!instancedMesh) return

    matrices.forEach((matrix, matrixIndex) => {
      instancedMesh.setMatrixAt(matrixIndex, matrix)
    })
    instancedMesh.instanceMatrix.needsUpdate = true
  }, [matrices])

  return (
    <group
      position={CARPET_POSITION}
      rotation={CARPET_ROTATION}
    >
      <mesh
        raycast={DISABLED_RAYCAST}
        rotation={[-Math.PI / 2, 0, 0]}
      >
        <planeGeometry args={[CARPET_SIZE.width, CARPET_SIZE.depth, 1, 1]} />
        <meshStandardMaterial
          color="#eaddee"
          emissive="#d8c1e2"
          emissiveIntensity={0.035}
          metalness={0}
          polygonOffset
          polygonOffsetFactor={-1}
          polygonOffsetUnits={-1}
          roughness={1}
          toneMapped
        />
      </mesh>
      <instancedMesh
        ref={instancedMeshRef}
        args={[undefined, undefined, instanceCount]}
        frustumCulled={false}
        raycast={DISABLED_RAYCAST}
      >
        <cylinderGeometry
          args={[
            carpetFurProfile.fiberRadius * 0.65,
            carpetFurProfile.fiberRadius,
            carpetFurProfile.fiberHeight,
            3,
          ]}
        />
        <meshStandardMaterial
          color="#f0e5f3"
          emissive="#d9c0e4"
          emissiveIntensity={0.045}
          metalness={0}
          roughness={1}
          toneMapped
        />
      </instancedMesh>
    </group>
  )
}
