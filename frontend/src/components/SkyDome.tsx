import { useEffect, useMemo, useState } from 'react'
import { useThree } from '@react-three/fiber'
import {
  BackSide,
  ClampToEdgeWrapping,
  LinearFilter,
  LoadingManager,
  RepeatWrapping,
  SRGBColorSpace,
  type Texture,
  TextureLoader,
} from 'three'

const DEFAULT_SKY_TEXTURE_URL = '/textures/dreamy-sky-360.webp'
const DEFAULT_SKY_RADIUS = 100
const DEFAULT_DOME_SCALE: [number, number, number] = [1, 1, 1]
const DEFAULT_TEXTURE_OFFSET: [number, number] = [0, 0]
const DEFAULT_TEXTURE_REPEAT: [number, number] = [1, 1]
const SKY_WIDTH_SEGMENTS = 128
const SKY_HEIGHT_SEGMENTS = 64

const skyDomeLoadingManager = new LoadingManager()

interface SkyDomeProps {
  textureUrl?: string
  radius?: number
  rotationY?: number
  domeScale?: [number, number, number]
  textureOffset?: [number, number]
  textureRepeat?: [number, number]
}

function useSkyDomeTexture(
  textureUrl: string,
  maxAnisotropy: number,
  textureOffsetX: number,
  textureOffsetY: number,
  textureRepeatX: number,
  textureRepeatY: number,
) {
  const textureLoader = useMemo(
    () => new TextureLoader(skyDomeLoadingManager),
    [],
  )
  const [skyTexture, setSkyTexture] = useState<Texture | null>(null)

  useEffect(() => {
    let cancelled = false

    ;(async () => {
      const loadedSkyTexture = await textureLoader.loadAsync(textureUrl)

      // Preserve the panorama's original browser-visible color.
      loadedSkyTexture.colorSpace = SRGBColorSpace
      // Repeat horizontally so the equirectangular seam joins cleanly.
      loadedSkyTexture.wrapS = RepeatWrapping
      // Clamp vertically to prevent pole-edge color bleeding.
      loadedSkyTexture.wrapT = ClampToEdgeWrapping
      // Tell Three.js to upload the changed texture settings.
      loadedSkyTexture.needsUpdate = true
      // Shift and crop the source without editing the image file.
      loadedSkyTexture.offset.set(textureOffsetX, textureOffsetY)
      loadedSkyTexture.repeat.set(textureRepeatX, textureRepeatY)
      // Avoid mipmap softening because the sky is a full-screen backdrop.
      loadedSkyTexture.generateMipmaps = false
      loadedSkyTexture.minFilter = LinearFilter
      loadedSkyTexture.magFilter = LinearFilter
      // Improve angled sampling on high-DPI screens without overdoing mobile cost.
      loadedSkyTexture.anisotropy = Math.min(maxAnisotropy, 8)

      if (cancelled) {
        loadedSkyTexture.dispose()
        return
      }

      setSkyTexture(loadedSkyTexture)
    })()

    return () => {
      cancelled = true
    }
  }, [
    maxAnisotropy,
    textureLoader,
    textureOffsetX,
    textureOffsetY,
    textureRepeatX,
    textureRepeatY,
    textureUrl,
  ])

  useEffect(() => {
    return () => {
      skyTexture?.dispose()
    }
  }, [skyTexture])

  return skyTexture
}

export default function SkyDome({
  textureUrl = DEFAULT_SKY_TEXTURE_URL,
  radius = DEFAULT_SKY_RADIUS,
  rotationY = 0,
  domeScale = DEFAULT_DOME_SCALE,
  textureOffset = DEFAULT_TEXTURE_OFFSET,
  textureRepeat = DEFAULT_TEXTURE_REPEAT,
}: SkyDomeProps) {
  const { gl } = useThree()
  const skyTexture = useSkyDomeTexture(
    textureUrl,
    gl.capabilities.getMaxAnisotropy(),
    textureOffset[0],
    textureOffset[1],
    textureRepeat[0],
    textureRepeat[1],
  )

  if (!skyTexture) {
    return null
  }

  return (
    <mesh
      rotation={[0, rotationY, 0]}
      scale={domeScale}
      frustumCulled={false}
      renderOrder={-100}
    >
      <sphereGeometry args={[radius, SKY_WIDTH_SEGMENTS, SKY_HEIGHT_SEGMENTS]} />
      <meshBasicMaterial
        map={skyTexture}
        side={BackSide}
        toneMapped={false}
        fog={false}
        depthWrite={false}
      />
    </mesh>
  )
}
