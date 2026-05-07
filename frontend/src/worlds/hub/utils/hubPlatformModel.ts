import {
  Box3,
  Mesh,
  MeshStandardMaterial,
  Vector3,
  type Object3D,
} from 'three'
import {
  HUB_ASSET_MATERIAL_STYLES,
  HUB_ASSET_MESH_STYLES,
  HUB_CENTER_NEMONIC_PLACEHOLDER_MATERIAL_NAMES,
  HUB_OUTER_BOARD_MESH_NAMES,
} from '../constants'
import {
  createOrganicBoardGroup,
  createRaisedCenterDisk,
} from './hubPlatformGeometry'
import {
  disableMeshShadows,
  disposeMesh,
  getMeshMaterials,
  isMesh,
} from './threeHelpers'

const PLATFORM_TARGET_WIDTH = 8.9
const PLATFORM_BASE_Y = -0.8

export interface PreparedPlatformModel {
  platform: Object3D
  position: [number, number, number]
  scale: number
}

function isCenterNemonicPlaceholder(mesh: Mesh) {
  if (mesh.name === 'CenterCube') return true

  return getMeshMaterials(mesh).some((material) => (
    HUB_CENTER_NEMONIC_PLACEHOLDER_MATERIAL_NAMES.has(material.name)
  ))
}

function applyPlatformPalette(mesh: Mesh) {
  const sourceMaterial = getMeshMaterials(mesh)[0]
  if (!sourceMaterial) return

  const material = sourceMaterial.clone() as MeshStandardMaterial
  const style = HUB_ASSET_MESH_STYLES[mesh.name]
    ?? HUB_ASSET_MATERIAL_STYLES[sourceMaterial.name]

  if (style) {
    material.color.set(style.color)
    if (material.emissive) {
      material.emissive.set(style.emissive)
      material.emissiveIntensity = style.emissiveIntensity
    }
  }

  material.roughness = 0.92
  material.metalness = 0
  mesh.material = material
}

export function prepareHubPlatformModel(source: Object3D): PreparedPlatformModel {
  const platform = source.clone(true)
  const bounds = new Box3().setFromObject(platform)
  const size = new Vector3()
  const center = new Vector3()
  bounds.getSize(size)
  bounds.getCenter(center)

  const maxDimension = Math.max(size.x, size.y, size.z)
  const modelScale = PLATFORM_TARGET_WIDTH / maxDimension

  const centerPlaceholderMeshes: Mesh[] = []
  platform.traverse((child) => {
    if (!isMesh(child)) return

    disableMeshShadows(child)
    if (HUB_OUTER_BOARD_MESH_NAMES.has(child.name)) {
      child.visible = false
      return
    }
    if (child.name === 'CenterDisk') {
      centerPlaceholderMeshes.push(child)
      return
    }
    if (isCenterNemonicPlaceholder(child)) {
      centerPlaceholderMeshes.push(child)
      return
    }

    applyPlatformPalette(child)
  })

  centerPlaceholderMeshes.forEach(disposeMesh)
  platform.add(createOrganicBoardGroup())
  platform.add(createRaisedCenterDisk())

  return {
    platform,
    position: [-center.x, PLATFORM_BASE_Y, -center.z],
    scale: modelScale,
  }
}
