import { useMemo } from 'react'
import { useGLTF } from '@react-three/drei'
import { Box3, Mesh, Vector3, type Object3D } from 'three'
import { HUB_COMMUNITY_CANVAS_BOOK_MAX_SIZE } from '../constants'

export const COMMUNITY_CANVAS_BOOK_MODEL_URL = '/models/community_canvas_book_clay.glb'

function isMesh(object: Object3D): object is Mesh {
  return (object as Mesh).isMesh === true
}

function prepareCommunityCanvasBookModel(source: Object3D) {
  const model = source.clone(true)
  model.name = 'CommunityCanvasBookAsset'
  model.updateMatrixWorld(true)

  const bounds = new Box3().setFromObject(model)
  const size = new Vector3()
  const center = new Vector3()
  bounds.getSize(size)
  bounds.getCenter(center)

  const horizontalSize = Math.max(size.x, size.z)
  const modelScale = HUB_COMMUNITY_CANVAS_BOOK_MAX_SIZE / horizontalSize
  model.scale.setScalar(modelScale)
  model.position.set(
    -center.x * modelScale,
    -bounds.min.y * modelScale,
    -center.z * modelScale,
  )

  model.traverse((child) => {
    if (!isMesh(child)) return
    child.castShadow = false
    child.receiveShadow = false
  })

  return model
}

export function useCommunityCanvasBookModel() {
  const gltf = useGLTF(COMMUNITY_CANVAS_BOOK_MODEL_URL)

  return useMemo(
    () => prepareCommunityCanvasBookModel(gltf.scene),
    [gltf.scene],
  )
}

export function preloadCommunityCanvasBookModel() {
  useGLTF.preload(COMMUNITY_CANVAS_BOOK_MODEL_URL)
}
