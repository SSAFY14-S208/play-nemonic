import { useGLTF } from '@react-three/drei'
import { type ReactNode, useMemo } from 'react'
import * as THREE from 'three'
import {
  HIDDEN_PREVIEW_OBJECT_KEYWORDS,
  ROOM_PREVIEW_MATERIALS,
  ROOM_PREVIEW_MODEL_PATH,
  ROOM_PREVIEW_MODEL_OFFSET,
  ROOM_PREVIEW_SCALE,
} from '../constants'

const MATERIAL_TEXTURE_MAP_KEYS = [
  'alphaMap',
  'aoMap',
  'bumpMap',
  'clearcoatMap',
  'clearcoatNormalMap',
  'clearcoatRoughnessMap',
  'displacementMap',
  'emissiveMap',
  'lightMap',
  'map',
  'metalnessMap',
  'normalMap',
  'roughnessMap',
  'transmissionMap',
] as const

function getMeshMaterials(material: THREE.Material | THREE.Material[]) {
  return Array.isArray(material) ? material : [material]
}

function cloneMeshMaterials(material: THREE.Material | THREE.Material[]) {
  return Array.isArray(material)
    ? material.map((meshMaterial) => meshMaterial.clone())
    : material.clone()
}

function isHiddenPreviewObject(objectName: string) {
  const normalizedObjectName = objectName.toLowerCase()

  return HIDDEN_PREVIEW_OBJECT_KEYWORDS.some((keyword) =>
    normalizedObjectName.includes(keyword),
  )
}

const INTENTIONAL_EMISSIVE_OBJECT_KEYWORDS = [
  'display',
  'glow',
  'led',
  'light strip',
  'moni_',
  'mouse_light',
  'mouse_wheel',
  'mousepad',
  'object_6.010',
  'screen',
]

const INTENTIONAL_EMISSIVE_MATERIAL_KEYWORDS = [
  'display',
  'emissive',
  'led',
  'light',
]

const ROOM_SURFACE_OBJECT_KEYWORDS = [
  'acoustic',
  'carpet',
  'desk_table',
  'shelf_body',
  'web_simple_back_wall',
  'web_simple_floor',
  'web_simple_left_wall',
]

function isTableLightStripObject(objectName: string) {
  return objectName.toLowerCase().includes('table light strip')
}

function isIntentionalEmissiveMaterial(
  material: THREE.MeshStandardMaterial,
  objectName: string,
) {
  const normalizedMaterialName = material.name.toLowerCase()
  const normalizedObjectName = objectName.toLowerCase()

  return (
    INTENTIONAL_EMISSIVE_MATERIAL_KEYWORDS.some((keyword) =>
      normalizedMaterialName.includes(keyword),
    ) ||
    INTENTIONAL_EMISSIVE_OBJECT_KEYWORDS.some((keyword) =>
      normalizedObjectName.includes(keyword),
    )
  )
}

function isRoomSurfaceMaterial(objectName: string) {
  const normalizedObjectName = objectName.toLowerCase()

  return ROOM_SURFACE_OBJECT_KEYWORDS.some((keyword) =>
    normalizedObjectName.includes(keyword),
  )
}

function preserveSourceMaterial(material: THREE.Material, objectName: string) {
  if (!(material instanceof THREE.MeshStandardMaterial)) return

  material.toneMapped = true
  material.envMapIntensity = Math.min(
    Math.max(
      material.envMapIntensity ||
        ROOM_PREVIEW_MATERIALS.defaultEnvironmentIntensity,
      ROOM_PREVIEW_MATERIALS.minEnvironmentIntensity,
    ),
    ROOM_PREVIEW_MATERIALS.maxEnvironmentIntensity,
  )

  if (isTableLightStripObject(objectName)) {
    material.map = null
    material.emissiveMap = null
    material.metalnessMap = null
    material.roughnessMap = null
    material.color.set(ROOM_PREVIEW_MATERIALS.tableLightStripColor)
    material.emissive.set(ROOM_PREVIEW_MATERIALS.tableLightStripColor)
    material.emissiveIntensity =
      ROOM_PREVIEW_MATERIALS.tableLightStripEmissiveIntensity
    material.metalness = 0
    material.roughness = 0.18
    material.side = THREE.DoubleSide
    material.toneMapped = false
    material.needsUpdate = true
    return
  }

  if (isEmissiveMaterial(material)) {
    material.toneMapped = false

    if (isIntentionalEmissiveMaterial(material, objectName)) {
      material.emissiveIntensity = Math.max(
        material.emissiveIntensity,
        ROOM_PREVIEW_MATERIALS.emissiveIntensityFloor,
      )
    } else if (isRoomSurfaceMaterial(objectName)) {
      material.toneMapped = true
      material.color.multiplyScalar(ROOM_PREVIEW_MATERIALS.roomSurfaceColorLift)
      material.emissiveIntensity =
        ROOM_PREVIEW_MATERIALS.roomSurfaceEmissiveIntensity
    } else {
      material.toneMapped = true
      material.emissiveIntensity = Math.min(
        material.emissiveIntensity,
        ROOM_PREVIEW_MATERIALS.nonGlowEmissiveIntensity,
      )
    }
  }

  MATERIAL_TEXTURE_MAP_KEYS.forEach((textureMapKey) => {
    const texture = (material as unknown as Record<string, unknown>)[
      textureMapKey
    ]

    if (!(texture instanceof THREE.Texture)) return
    if (!(texture.matrix instanceof THREE.Matrix3)) {
      texture.matrix = new THREE.Matrix3()
    }
    texture.matrixAutoUpdate = true
    texture.anisotropy = ROOM_PREVIEW_MATERIALS.textureAnisotropy
    texture.needsUpdate = true
  })

  material.needsUpdate = true
}

function isEmissiveMaterial(material: THREE.MeshStandardMaterial) {
  const materialName = material.name.toLowerCase()
  const hasEmissiveColor =
    material.emissive.r > 0 || material.emissive.g > 0 || material.emissive.b > 0

  return hasEmissiveColor || materialName.includes('emissive')
}

type RoomPreviewModelChildren =
  | ReactNode
  | ((configuredScene: THREE.Object3D) => ReactNode)

export default function RoomPreviewModel({
  children,
}: {
  children?: RoomPreviewModelChildren
}) {
  const { scene } = useGLTF(ROOM_PREVIEW_MODEL_PATH)

  const configuredScene = useMemo(() => {
    scene.traverse((object) => {
      if (!(object instanceof THREE.Mesh)) return

      object.visible = !isHiddenPreviewObject(object.name)
      object.castShadow = true
      object.receiveShadow = true
      object.frustumCulled = false
      object.material = cloneMeshMaterials(object.material)
      getMeshMaterials(object.material).forEach((material) => {
        preserveSourceMaterial(material, object.name)
      })
    })

    return scene
  }, [scene])

  return (
    <group
      position={ROOM_PREVIEW_MODEL_OFFSET}
      scale={ROOM_PREVIEW_SCALE}
    >
      <primitive object={configuredScene} />
      {typeof children === 'function' ? children(configuredScene) : children}
    </group>
  )
}

useGLTF.preload(ROOM_PREVIEW_MODEL_PATH)
