import { useGLTF } from '@react-three/drei'
import { useMemo } from 'react'
import { Box3, Mesh, Vector3, type Group, type Object3D } from 'three'

import { FORTUNE_POPO_MODEL_HEIGHT, FORTUNE_POPO_MODEL_PATH } from '../constants'

export function useFortunePopoModel() {
  const { scene } = useGLTF(FORTUNE_POPO_MODEL_PATH)

  return useMemo(() => prepareFortunePopoModel(scene), [scene])
}

export function preloadFortunePopoModel() {
  useGLTF.preload(FORTUNE_POPO_MODEL_PATH)
}

function prepareFortunePopoModel(source: Object3D) {
  const model = source.clone(true) as Group
  model.updateMatrixWorld(true)

  const bounds = new Box3().setFromObject(model)
  const modelSize = new Vector3()
  const modelCenter = new Vector3()
  bounds.getSize(modelSize)
  bounds.getCenter(modelCenter)

  const modelScale = FORTUNE_POPO_MODEL_HEIGHT / Math.max(modelSize.x, modelSize.y, modelSize.z)
  model.scale.setScalar(modelScale)
  model.position.set(
    -modelCenter.x * modelScale,
    -bounds.min.y * modelScale,
    -modelCenter.z * modelScale,
  )

  model.traverse((child) => {
    if (!(child instanceof Mesh)) return

    child.castShadow = true
    child.receiveShadow = true
  })

  return model
}
