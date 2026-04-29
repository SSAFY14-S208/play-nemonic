import { Suspense, useMemo, useRef } from 'react'
import { BufferAttribute, BufferGeometry, Color, Group, PointsMaterial } from 'three'
import HubPlatformMesh from './objects/HubPlatformMesh'
import { useHubViewportControls } from './useHubViewportControls'

function NightStarField() {
  const stars = useMemo(() => {
    const starCount = 150
    const positions = new Float32Array(starCount * 3)
    const colors = new Float32Array(starCount * 3)
    const geometry = new BufferGeometry()
    const palette = [
      new Color('#fff8dc'),
      new Color('#ffffff'),
      new Color('#cfe3ff'),
      new Color('#ffd9f1'),
    ]

    for (let index = 0; index < starCount; index += 1) {
      const seedA = Math.sin((index + 1) * 12.9898) * 43758.5453
      const seedB = Math.sin((index + 5) * 78.233) * 24634.6345
      const seedC = Math.sin((index + 11) * 37.719) * 13548.721
      const starX = (seedA - Math.floor(seedA) - 0.5) * 30
      const starY = 3.8 + (seedB - Math.floor(seedB)) * 9.5
      const starZ = -8 - (seedC - Math.floor(seedC)) * 18
      const color = palette[index % palette.length]

      positions[index * 3] = starX
      positions[index * 3 + 1] = starY
      positions[index * 3 + 2] = starZ
      colors[index * 3] = color.r
      colors[index * 3 + 1] = color.g
      colors[index * 3 + 2] = color.b
    }

    geometry.setAttribute('position', new BufferAttribute(positions, 3))
    geometry.setAttribute('color', new BufferAttribute(colors, 3))

    return {
      geometry,
      material: new PointsMaterial({
        size: 0.075,
        sizeAttenuation: true,
        vertexColors: true,
        transparent: true,
        opacity: 0.86,
        depthWrite: false,
        fog: false,
      }),
    }
  }, [])

  return <points geometry={stars.geometry} material={stars.material} renderOrder={-10} />
}

export default function HubScene() {
  const modelRootRef = useRef<Group>(null)
  useHubViewportControls(modelRootRef)

  return (
    <>
      <fog attach="fog" args={['#262449', 18, 34]} />
      <hemisphereLight args={['#fffef8', '#d7d3cd', 1.7]} />
      <directionalLight color="#ffffff" intensity={2.2} position={[6, 8, 8]} />
      <directionalLight color="#f2f7ff" intensity={1.2} position={[-8, 3, -4]} />
      <NightStarField />
      <mesh position={[0, -2.58, 0]}>
        <cylinderGeometry args={[4.5, 5.5, 0.25, 96]} />
        <meshStandardMaterial color="#ece8de" roughness={0.95} metalness={0} />
      </mesh>
      <group ref={modelRootRef}>
        <Suspense fallback={null}>
          <HubPlatformMesh />
        </Suspense>
      </group>
    </>
  )
}
