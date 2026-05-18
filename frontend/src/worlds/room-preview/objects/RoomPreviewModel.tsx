import { useGLTF } from '@react-three/drei'
import { useThree, type ThreeEvent } from '@react-three/fiber'
import { type ReactNode, useEffect, useMemo } from 'react'
import * as THREE from 'three'
import {
  HIDDEN_PREVIEW_OBJECT_KEYWORDS,
  ROOM_PREVIEW_MATERIALS,
  ROOM_PREVIEW_MODEL_PATH,
  ROOM_PREVIEW_MODEL_OFFSET,
  ROOM_PREVIEW_SCALE,
} from '../constants'
import { useRoomPreviewLightDebugStore } from '../light-debug'

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

function isHiddenPreviewObject(objectName: string) {
  const normalizedObjectName = objectName.toLowerCase()

  return HIDDEN_PREVIEW_OBJECT_KEYWORDS.some((keyword) =>
    normalizedObjectName.includes(keyword),
  )
}

function isTableLightStripObject(objectName: string) {
  return objectName.toLowerCase().includes('table light strip')
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
    material.emissiveIntensity = Math.max(
      material.emissiveIntensity,
      ROOM_PREVIEW_MATERIALS.emissiveIntensityFloor,
    )
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
  onClick,
  onPointerOut,
  onPointerOver,
}: {
  children?: RoomPreviewModelChildren
  onClick?: (event: ThreeEvent<MouseEvent>) => void
  onPointerOut?: (event: ThreeEvent<PointerEvent>) => void
  onPointerOver?: (event: ThreeEvent<PointerEvent>) => void
}) {
  const { scene } = useGLTF(ROOM_PREVIEW_MODEL_PATH)
  const invalidate = useThree((state) => state.invalidate)
  const isFrustumCullingEnabled = useRoomPreviewLightDebugStore(
    (state) => state.isFrustumCullingEnabled,
  )

  const configuredScene = useMemo(() => {
    scene.traverse((object) => {
      if (!(object instanceof THREE.Mesh)) return

      object.visible = !isHiddenPreviewObject(object.name)
      getMeshMaterials(object.material).forEach((material) => {
        preserveSourceMaterial(material, object.name)
      })
    })

    return scene
  }, [scene])

  useEffect(() => {
    scene.traverse((object) => {
      if (!(object instanceof THREE.Mesh)) return
      object.frustumCulled = isFrustumCullingEnabled
    })
    invalidate()
  }, [invalidate, isFrustumCullingEnabled, scene])

  return (
    <group
      position={ROOM_PREVIEW_MODEL_OFFSET}
      scale={ROOM_PREVIEW_SCALE}
      onClick={onClick}
      onPointerOut={onPointerOut}
      onPointerOver={onPointerOver}
    >
      <primitive object={configuredScene} />
      {typeof children === 'function' ? children(configuredScene) : children}
    </group>
  )
}

useGLTF.preload(ROOM_PREVIEW_MODEL_PATH)
