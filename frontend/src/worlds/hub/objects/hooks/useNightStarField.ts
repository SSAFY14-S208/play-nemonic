import { useMemo } from 'react'
import { BufferAttribute, BufferGeometry, Color, PointsMaterial } from 'three'

const NIGHT_STAR_COUNT = 150
const NIGHT_STAR_PALETTE = [
  new Color('#fff8dc'),
  new Color('#ffffff'),
  new Color('#cfe3ff'),
  new Color('#ffd9f1'),
]

function getPseudoRandomSeed(index: number, multiplier: number, amplitude: number) {
  const rawSeed = Math.sin(index * multiplier) * amplitude

  return rawSeed - Math.floor(rawSeed)
}

export function useNightStarField() {
  return useMemo(() => {
    const positions = new Float32Array(NIGHT_STAR_COUNT * 3)
    const colors = new Float32Array(NIGHT_STAR_COUNT * 3)
    const geometry = new BufferGeometry()

    for (let starIndex = 0; starIndex < NIGHT_STAR_COUNT; starIndex += 1) {
      const seedA = getPseudoRandomSeed(starIndex + 1, 12.9898, 43758.5453)
      const seedB = getPseudoRandomSeed(starIndex + 5, 78.233, 24634.6345)
      const seedC = getPseudoRandomSeed(starIndex + 11, 37.719, 13548.721)
      const color = NIGHT_STAR_PALETTE[starIndex % NIGHT_STAR_PALETTE.length]

      positions[starIndex * 3] = (seedA - 0.5) * 30
      positions[starIndex * 3 + 1] = 3.8 + seedB * 9.5
      positions[starIndex * 3 + 2] = -8 - seedC * 18
      colors[starIndex * 3] = color.r
      colors[starIndex * 3 + 1] = color.g
      colors[starIndex * 3 + 2] = color.b
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
}
