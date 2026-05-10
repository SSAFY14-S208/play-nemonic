import { useFrame } from '@react-three/fiber'
import { type ShaderMaterial, type Mesh } from 'three'
import { type RefObject } from 'react'
import { HUB_WATER_WAVE_SPEED } from '../../constants'

/**
 * three.js Sky-water 셰이더의 time uniform을 매 프레임 진행시킨다.
 * 0.6 곱은 잔잔한 표면 흐름 속도. 더 빠르거나 느리게 하려면
 * HUB_WATER_WAVE_SPEED 값을 조정한다.
 */
export function useThreeWaterTime(waterRef: RefObject<Mesh | null>) {
  useFrame((_, delta) => {
    const water = waterRef.current
    if (!water) return
    const material = water.material as ShaderMaterial
    material.uniforms.time.value += delta * HUB_WATER_WAVE_SPEED
  })
}
