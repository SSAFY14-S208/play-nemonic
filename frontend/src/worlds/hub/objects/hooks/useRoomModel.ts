import { useEffect } from 'react'
import { useThree } from '@react-three/fiber'
import * as THREE from 'three'
import { HUB_PERFORMANCE_PROFILES } from '@/shared/constants'
import type { HubPerformanceMode } from '@/shared/types'
import { logHubMaterialStats, trackHubInvalidate } from '@/shared/utils'

const EMISSIVE_MESH_KEYWORDS = [
  'light',
  'backlight',
  'strip',
  'keyboard',
  'mouse_light',
  'red emitting',
  'led',
  'logo',
  'screen',
]

const CAST_SHADOW_KEYWORDS = [
  'monitor',
  'table',
  'keyboard',
  'mouse',
  'speaker',
  'shelf',
  'panel',
  'wall',
  'acoustic',
  'plant',
  'carpet',
  'pc',
]

const WALL_DETAIL_KEYWORDS = ['panel', 'wall', 'acoustic']

const KEEP_DOUBLE_SIDED_KEYWORDS = [
  'curtain',
  'flower',
  'glass',
  'leaf',
  'note',
  'paper',
  'plant',
  'screen',
  'tape',
]

const IDLE_POSE_ANIMATION_NAMES = ['print_head_up', 'label_up'] as const
const DISABLED_RAYCAST: THREE.Mesh['raycast'] = () => undefined

function includesAnyKeyword(value: string, keywords: string[]) {
  const normalizedValue = value.toLowerCase()

  return keywords.some((keyword) => normalizedValue.includes(keyword))
}

function getMeshMaterials(material: THREE.Material | THREE.Material[]) {
  return Array.isArray(material) ? material : [material]
}

function isInternalPrintLabelObject(object: THREE.Object3D) {
  const parentName = object.parent?.name.toLowerCase() ?? ''
  const objectName = object.name.toLowerCase()

  return parentName.includes('post_it_label') || objectName.includes('post_it_label')
}

function isStaticPrinterPaperObject(object: THREE.Object3D) {
  return object.name.toLowerCase().includes('nemonic_cartridge_paper')
}

function setInternalPrintLabelVisible(scene: THREE.Object3D, isVisible: boolean) {
  scene.traverse((child) => {
    if (!isInternalPrintLabelObject(child)) return
    child.visible = isVisible
  })
}

function hideStaticPrinterPaper(scene: THREE.Object3D) {
  scene.traverse((child) => {
    if (!isStaticPrinterPaperObject(child)) return
    child.visible = false
  })
}

function applyAnimationTrackValue(
  targetObject: THREE.Object3D,
  propertyName: string | undefined,
  value: number[],
) {
  if (propertyName === 'position') {
    targetObject.position.fromArray(value)
    return
  }

  if (propertyName === 'quaternion') {
    targetObject.quaternion.fromArray(value).normalize()
    return
  }

  if (propertyName === 'scale') {
    targetObject.scale.fromArray(value)
  }
}

function restoreRoomAssetPose(
  scene: THREE.Object3D,
  animations: THREE.AnimationClip[],
) {
  animations
    .filter((animation) =>
      IDLE_POSE_ANIMATION_NAMES.some(
        (animationName) => animation.name === animationName,
      ),
    )
    .forEach((animation) => {
      animation.tracks.forEach((track) => {
        const [targetName, propertyName] = track.name.split('.')
        const targetObject = scene.getObjectByName(targetName)
        if (!targetObject) return

        const firstValue = Array.from(track.values.slice(0, track.getValueSize()))
        applyAnimationTrackValue(targetObject, propertyName, firstValue)
      })
    })

  scene.updateMatrixWorld(true)
}

function restoreRoomPrintIdlePose(
  scene: THREE.Object3D,
  animations: THREE.AnimationClip[],
) {
  restoreRoomAssetPose(scene, animations)

  const printHeadAnimation = animations.find(
    (animation) => animation.name === 'print_head_up',
  )
  if (!printHeadAnimation) return

  printHeadAnimation.tracks.forEach((track) => {
    const [targetName, propertyName] = track.name.split('.')
    const targetObject = scene.getObjectByName(targetName)
    if (!targetObject) return

    const valueSize = track.getValueSize()
    const lastValueStart = track.values.length - valueSize
    const lastValue = Array.from(track.values.slice(lastValueStart))

    applyAnimationTrackValue(targetObject, propertyName, lastValue)
  })

  scene.updateMatrixWorld(true)
}

function configureRoomMaterial(
  child: THREE.Mesh,
  material: THREE.MeshStandardMaterial,
  maxAnisotropy: number,
  performanceMode: HubPerformanceMode,
) {
  const performanceProfile = HUB_PERFORMANCE_PROFILES[performanceMode]
  const materialIdentity = `${child.name} ${material.name}`
  const isEmissiveMesh = includesAnyKeyword(
    materialIdentity,
    EMISSIVE_MESH_KEYWORDS,
  )
  const hasExportedEmission =
    Boolean(material.emissiveMap) || material.emissive.getHex() !== 0

  if (isEmissiveMesh) {
    const materialName = material.name.toLowerCase()
    const objectName = child.name.toLowerCase()
    const isLogo = materialName.includes('logo') || objectName.includes('logo')
    const isScreen = materialName.includes('screen') || objectName.includes('screen')
    const isLed = materialName.includes('led') || objectName.includes('led')

    material.emissive = new THREE.Color(
      isLogo ? '#fff6ff' : isScreen || isLed ? '#bfefff' : '#d8c5ff',
    )
    material.emissiveIntensity = isLogo
      ? 2.1
      : isScreen || isLed
        ? 1.05
        : materialName.includes('red')
          ? 0.72
          : 0.46
  }

  if (isEmissiveMesh || hasExportedEmission) {
    material.toneMapped = false
  }

  if (
    !performanceProfile.environment &&
    material instanceof THREE.MeshPhysicalMaterial
  ) {
    material.transmission = 0
    material.thickness = 0
  }

  if (material.opacity >= 1) {
    material.transparent = false
    material.depthWrite = true
  }

  if (
    !performanceProfile.environment &&
    material.opacity >= 1 &&
    !includesAnyKeyword(materialIdentity, KEEP_DOUBLE_SIDED_KEYWORDS)
  ) {
    material.side = THREE.FrontSide
  }

  if (!performanceProfile.environment) {
    material.envMapIntensity = Math.min(material.envMapIntensity, 0.18)
  }

  if (
    includesAnyKeyword(materialIdentity, WALL_DETAIL_KEYWORDS) &&
    !material.userData.nemonicWallAdjusted
  ) {
    material.color.lerp(new THREE.Color('#f3ecff'), 0.28)
    material.roughness = Math.max(material.roughness, 0.68)
    material.envMapIntensity = Math.min(material.envMapIntensity, 0.28)
    material.userData.nemonicWallAdjusted = true
  }

  ;[
    material.map,
    material.normalMap,
    material.roughnessMap,
    material.metalnessMap,
  ].forEach((texture) => {
    if (!texture) return

    texture.anisotropy = performanceProfile.anisotropyLimit
      ? Math.min(maxAnisotropy, performanceProfile.anisotropyLimit)
      : maxAnisotropy
    texture.needsUpdate = true
  })

  material.needsUpdate = true
}

function configureRoomMesh(
  child: THREE.Mesh,
  maxAnisotropy: number,
  performanceMode: HubPerformanceMode,
) {
  const performanceProfile = HUB_PERFORMANCE_PROFILES[performanceMode]

  child.raycast = DISABLED_RAYCAST

  if (includesAnyKeyword(child.name, ['volumetric', 'turn on for world lighting'])) {
    child.visible = false
    return
  }

  const shouldCastShadow =
    performanceProfile.shadows &&
    includesAnyKeyword(child.name, CAST_SHADOW_KEYWORDS)

  child.castShadow = shouldCastShadow
  child.receiveShadow = performanceProfile.shadows && shouldCastShadow

  getMeshMaterials(child.material).forEach((material) => {
    if (!(material instanceof THREE.MeshStandardMaterial)) return

    configureRoomMaterial(child, material, maxAnisotropy, performanceMode)
  })
}

export function useRoomModel(
  scene: THREE.Object3D,
  animations: THREE.AnimationClip[],
  performanceMode: HubPerformanceMode,
) {
  const { gl, invalidate } = useThree()

  useEffect(() => {
    const maxAnisotropy = gl.capabilities.getMaxAnisotropy()

    scene.traverse((child) => {
      if (!(child instanceof THREE.Mesh)) return

      configureRoomMesh(child, maxAnisotropy, performanceMode)
    })

    restoreRoomPrintIdlePose(scene, animations)
    setInternalPrintLabelVisible(scene, false)
    hideStaticPrinterPaper(scene)
    logHubMaterialStats(scene, performanceMode, 'room')
    trackHubInvalidate('roomModel.materialSetup')
    invalidate()
  }, [animations, gl, invalidate, performanceMode, scene])
}
