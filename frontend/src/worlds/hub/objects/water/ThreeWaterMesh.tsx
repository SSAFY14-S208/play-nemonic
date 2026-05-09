'use client'

import { useEffect, useMemo, useRef } from 'react'
import { useFrame, useLoader } from '@react-three/fiber'
import { Water } from 'three/examples/jsm/objects/Water.js'
import {
  PlaneGeometry,
  RepeatWrapping,
  TextureLoader,
  Vector3,
  type Mesh,
  type ShaderMaterial,
} from 'three'
import { HUB_WATER_PLANE_SIZE, HUB_WATER_SURFACE_Y } from '../../constants'

const WATER_NORMALS_URL = '/textures/waternormals.jpg'

export default function ThreeWaterMesh() {
  const waterRef = useRef<Mesh>(null)
  const normalsTexture = useLoader(TextureLoader, WATER_NORMALS_URL)

  const water = useMemo(() => {
    normalsTexture.wrapS = RepeatWrapping
    normalsTexture.wrapT = RepeatWrapping

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
    return () => {
      water.geometry.dispose()
      const material = water.material as ShaderMaterial
      material.dispose()
    }
  }, [water])

  useFrame((_, delta) => {
    const material = water.material as ShaderMaterial
    material.uniforms.time.value += delta * 0.6
  })

  return <primitive ref={waterRef} object={water} />
}
