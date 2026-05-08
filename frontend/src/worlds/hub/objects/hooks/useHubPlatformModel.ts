import { useMemo } from 'react'
import { useGLTF } from '@react-three/drei'
import {
  prepareHubPlatformModel,
} from '../../utils'

const PLATFORM_MODEL_URL = '/models/pastel_platform.glb'

export function useHubPlatformModel() {
  const gltf = useGLTF(PLATFORM_MODEL_URL)

  return useMemo(
    () => prepareHubPlatformModel(gltf.scene),
    [gltf.scene],
  )
}

export function preloadHubPlatformModel() {
  useGLTF.preload(PLATFORM_MODEL_URL)
}
