import { useEffect, useMemo, useRef, useState } from 'react'
import { Water } from 'three/examples/jsm/objects/Water.js'
import {
  PlaneGeometry,
  RepeatWrapping,
  type Texture,
  TextureLoader,
  Vector3,
  type Mesh,
  type ShaderMaterial,
} from 'three'
import {
  HUB_WATER_PLANE_SIZE,
  HUB_WATER_SURFACE_Y,
} from '../constants'
import { useThreeWaterTime } from './hooks'

const WATER_NORMALS_URL = '/textures/waternormals.jpg'

export default function ThreeWaterMesh() {
  const waterRef = useRef<Mesh>(null)
  // useLoader 대신 직접 비동기 로드. useLoader가 반환한 텍스처를 mutate하면
  // React Compiler의 react-hooks/immutability 룰에 걸리므로, 우리가 만든
  // Texture 인스턴스에 wrap을 설정한 뒤 setState로 노출한다.
  const [normalsTexture, setNormalsTexture] = useState<Texture | null>(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      const loader = new TextureLoader()
      const loaded = await loader.loadAsync(WATER_NORMALS_URL)
      loaded.wrapS = RepeatWrapping
      loaded.wrapT = RepeatWrapping
      loaded.needsUpdate = true
      if (cancelled) {
        loaded.dispose()
        return
      }
      setNormalsTexture(loaded)
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const water = useMemo(() => {
    if (!normalsTexture) return null
    const geometry = new PlaneGeometry(HUB_WATER_PLANE_SIZE, HUB_WATER_PLANE_SIZE)
    const instance = new Water(geometry, {
      textureWidth: 512,
      textureHeight: 512,
      waterNormals: normalsTexture,
      sunDirection: new Vector3(0.4, 0.85, 0.3).normalize(),
      sunColor: 0xfff4d6,
      waterColor: 0x224a6f,
      distortionScale: 2.6,
      fog: false,
      alpha: 1.0,
    })
    instance.rotation.x = -Math.PI / 2
    instance.position.y = HUB_WATER_SURFACE_Y
    return instance
  }, [normalsTexture])

  useEffect(() => {
    if (!water) return
    return () => {
      water.geometry.dispose()
      const material = water.material as ShaderMaterial
      material.dispose()
    }
  }, [water])

  useThreeWaterTime(waterRef)

  if (!water) return null
  return <primitive ref={waterRef} object={water} />
}
