import { useMemo } from 'react'
import { useGLTF } from '@react-three/drei'
import {
  Box3,
  Mesh,
  MeshStandardMaterial,
  Object3D,
  Vector3,
  type Material,
} from 'three'
import {
  HUB_CENTER_NEMONIC_BASE_Y,
  HUB_CENTER_NEMONIC_MAX_SIZE,
} from '../constants'

const CENTER_NEMONIC_MODEL_URL = '/models/nemonic-custom.glb'

function isMesh(object: Object3D): object is Mesh {
  return (object as Mesh).isMesh === true
}

function getMeshMaterials(mesh: Mesh): Material[] {
  if (!mesh.material) return []
  return Array.isArray(mesh.material) ? mesh.material : [mesh.material]
}

function prepareCenterNemonicMesh(mesh: Mesh) {
  if (!mesh.material) return

  mesh.castShadow = false
  mesh.receiveShadow = false
  mesh.renderOrder = 31
  mesh.material = Array.isArray(mesh.material)
    ? mesh.material.map((material) => material.clone())
    : mesh.material.clone()

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

function prepareCenterNemonicModel(source: Object3D) {
  const model = source.clone(true)
  model.name = 'CenterNemonicAsset'
  model.updateMatrixWorld(true)

  const bounds = new Box3().setFromObject(model)
  const size = new Vector3()
  const center = new Vector3()
  bounds.getSize(size)
  bounds.getCenter(center)

  const maxDimension = Math.max(size.x, size.y, size.z)
  const modelScale = HUB_CENTER_NEMONIC_MAX_SIZE / maxDimension
  model.scale.setScalar(modelScale)
  model.position.set(
    -center.x * modelScale,
    HUB_CENTER_NEMONIC_BASE_Y - bounds.min.y * modelScale,
    -center.z * modelScale,
  )

  model.traverse((child) => {
    if (!isMesh(child)) return
    prepareCenterNemonicMesh(child)
  })

  return model
}

export default function CenterNemonicMesh() {
  const gltf = useGLTF(CENTER_NEMONIC_MODEL_URL)
  const model = useMemo(() => prepareCenterNemonicModel(gltf.scene), [gltf.scene])

  return <primitive object={model} dispose={null} />
}

useGLTF.preload(CENTER_NEMONIC_MODEL_URL)
