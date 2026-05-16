import { useEffect, useMemo } from 'react'
import { useTexture } from '@react-three/drei'
import * as THREE from 'three'

const HUB_SKY_TEXTURE_URL = '/textures/dreamy-sky-360.webp'
const HUB_SKY_RADIUS = 68
const HUB_SKY_WIDTH_SEGMENTS = 96
const HUB_SKY_HEIGHT_SEGMENTS = 48

export default function HubSkyDome() {
  const sourceSkyTexture = useTexture(HUB_SKY_TEXTURE_URL)
  const skyTexture = useMemo(() => {
    const nextSkyTexture = sourceSkyTexture.clone()

    nextSkyTexture.colorSpace = THREE.SRGBColorSpace
    nextSkyTexture.wrapS = THREE.RepeatWrapping
    nextSkyTexture.wrapT = THREE.ClampToEdgeWrapping
    nextSkyTexture.offset.set(0.06, 0.08)
    nextSkyTexture.repeat.set(1, 0.72)
    nextSkyTexture.generateMipmaps = false
    nextSkyTexture.minFilter = THREE.LinearFilter
    nextSkyTexture.magFilter = THREE.LinearFilter
    nextSkyTexture.needsUpdate = true

    return nextSkyTexture
  }, [sourceSkyTexture])

  useEffect(() => {
    return () => {
      skyTexture.dispose()
    }
  }, [skyTexture])

  return (
    <mesh
      frustumCulled={false}
      renderOrder={-100}
      rotation={[0, Math.PI * 0.18, 0]}
    >
      <sphereGeometry
        args={[HUB_SKY_RADIUS, HUB_SKY_WIDTH_SEGMENTS, HUB_SKY_HEIGHT_SEGMENTS]}
      />
      <meshBasicMaterial
        depthWrite={false}
        fog={false}
        map={skyTexture}
        opacity={0.34}
        side={THREE.BackSide}
        toneMapped={false}
        transparent
      />
    </mesh>
  )
}
