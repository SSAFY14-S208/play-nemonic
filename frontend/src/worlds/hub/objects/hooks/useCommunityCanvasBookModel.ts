import { useMemo } from 'react'
import { useGLTF } from '@react-three/drei'
import { HUB_COMMUNITY_CANVAS_BOOK_MAX_SIZE } from '../../constants'
import {
  cloneMeshMaterials,
  disableMeshShadows,
  isMesh,
  prepareScaledGltfModel,
} from '../../utils'

const COMMUNITY_CANVAS_BOOK_MODEL_URL = '/models/community_canvas_book_clay.glb'

export function useCommunityCanvasBookModel() {
  const gltf = useGLTF(COMMUNITY_CANVAS_BOOK_MODEL_URL)

  return useMemo(
    () => prepareScaledGltfModel({
      source: gltf.scene,
      name: 'CommunityCanvasBookAsset',
      targetSize: HUB_COMMUNITY_CANVAS_BOOK_MAX_SIZE,
      scaleReference: 'horizontal',
      onTraverse: (child) => {
        if (!isMesh(child)) return
        cloneMeshMaterials(child)
        disableMeshShadows(child)
      },
    }),
    [gltf.scene],
  )
}

export function preloadCommunityCanvasBookModel() {
  useGLTF.preload(COMMUNITY_CANVAS_BOOK_MODEL_URL)
}
