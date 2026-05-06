import { useMemo } from 'react'
import { useGLTF } from '@react-three/drei'
import {
  MeshStandardMaterial,
  type Mesh,
} from 'three'
import {
  HUB_CENTER_NEMONIC_BASE_Y,
  HUB_CENTER_NEMONIC_MAX_SIZE,
} from '../../constants'
import {
  cloneMeshMaterials,
  disableMeshShadows,
  getMeshMaterials,
  isMesh,
  prepareScaledGltfModel,
} from '../../utils'

const CENTER_NEMONIC_MODEL_URL = '/models/nemonic-custom.glb'

function prepareCenterNemonicMesh(mesh: Mesh) {
  if (!mesh.material) return

  disableMeshShadows(mesh)
  mesh.renderOrder = 31
  cloneMeshMaterials(mesh)

  getMeshMaterials(mesh).forEach((material) => {
    const standardMaterial = material as MeshStandardMaterial
    standardMaterial.roughness = Math.min(standardMaterial.roughness ?? 0.86, 0.9)
    standardMaterial.metalness = standardMaterial.metalness ?? 0

    if (standardMaterial.emissive && standardMaterial.name?.includes('LED')) {
      standardMaterial.emissive.set('#e6fbff')
      standardMaterial.emissiveIntensity = 0.2
    }
  })
}

export function useCenterNemonicModel() {
  const gltf = useGLTF(CENTER_NEMONIC_MODEL_URL)

  return useMemo(
    () => prepareScaledGltfModel({
      source: gltf.scene,
      name: 'CenterNemonicAsset',
      targetSize: HUB_CENTER_NEMONIC_MAX_SIZE,
      baseY: HUB_CENTER_NEMONIC_BASE_Y,
      onTraverse: (child) => {
        if (!isMesh(child)) return
        prepareCenterNemonicMesh(child)
      },
    }),
    [gltf.scene],
  )
}

export function preloadCenterNemonicModel() {
  useGLTF.preload(CENTER_NEMONIC_MODEL_URL)
}
