import { useEffect, useRef } from 'react'
import { useThree } from '@react-three/fiber'
import * as THREE from 'three'
import type { AnimationAction } from 'three'
import { HUB_PERFORMANCE_PROFILES } from '@/shared/constants'
import { useHubPrintStore } from '@/shared/stores'
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

const ROOM_PRINT_ANIMATION_NAMES = [
  'print_head_up',
  'print_button_click',
  'label_up',
] as const
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

function isInternalPrintLabelMesh(mesh: THREE.Mesh) {
  return isInternalPrintLabelObject(mesh)
}

function clonePrintLabelMaterial(mesh: THREE.Mesh) {
  if (mesh.userData.nemonicPrintLabelMaterialCloned) return

  mesh.material = Array.isArray(mesh.material)
    ? mesh.material.map((material) => material.clone())
    : mesh.material.clone()
  mesh.userData.nemonicPrintLabelMaterialCloned = true
}

function applyPrintLabelTexture(scene: THREE.Object3D, texture: THREE.Texture) {
  scene.traverse((child) => {
    if (!(child instanceof THREE.Mesh)) return
    if (!isInternalPrintLabelMesh(child)) return

    clonePrintLabelMaterial(child)

    getMeshMaterials(child.material).forEach((material) => {
      if (!(material instanceof THREE.MeshStandardMaterial)) return

      material.color.set('#ffffff')
      material.emissive.set('#ffffff')
      material.emissiveIntensity = 0.16
      material.emissiveMap = texture
      material.map = texture
      material.metalness = 0
      material.roughness = Math.max(material.roughness, 0.68)
      material.side = THREE.DoubleSide
      material.toneMapped = false
      material.needsUpdate = true
    })
  })
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

function resetRoomPrintAnimations(
  actions: Record<string, AnimationAction | null>,
) {
  ROOM_PRINT_ANIMATION_NAMES.forEach((animationName) => {
    const action = actions[animationName]
    if (!action) return

    action.stop()
    action.reset()
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
  actions: Record<string, AnimationAction | null>,
  performanceMode: HubPerformanceMode,
) {
  const activeAnimationRequestIdRef = useRef<string | null>(null)
  const printLabelTextureRef = useRef<THREE.Texture | null>(null)
  const printLabelTextureSourceRef = useRef<string | null>(null)
  const { gl, invalidate } = useThree()
  const currentRequest = useHubPrintStore((state) => state.currentRequest)
  const printStatus = useHubPrintStore((state) => state.printStatus)

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

  useEffect(() => {
    const currentTextureSource = currentRequest?.imageDataUrl
    if (!currentTextureSource) return
    if (printLabelTextureSourceRef.current === currentTextureSource) return

    printLabelTextureSourceRef.current = currentTextureSource

    let isCancelled = false
    const textureLoader = new THREE.TextureLoader()
    const printLabelTexture = textureLoader.load(
      currentTextureSource,
      (loadedTexture) => {
        if (isCancelled) {
          loadedTexture.dispose()
          return
        }

        loadedTexture.needsUpdate = true
        trackHubInvalidate('roomModel.printLabelTextureLoaded')
        invalidate()
      },
    )

    printLabelTexture.colorSpace = THREE.SRGBColorSpace
    printLabelTexture.flipY = false
    printLabelTexture.needsUpdate = true

    printLabelTextureRef.current?.dispose()
    printLabelTextureRef.current = printLabelTexture

    applyPrintLabelTexture(scene, printLabelTexture)
    trackHubInvalidate('roomModel.printLabelTexture')
    invalidate()

    return () => {
      isCancelled = true
    }
  }, [currentRequest?.imageDataUrl, invalidate, scene])

  useEffect(() => {
    const shouldShowInternalPrintLabel =
      Boolean(currentRequest) &&
      (printStatus === 'requested' || printStatus === 'printing')

    setInternalPrintLabelVisible(scene, shouldShowInternalPrintLabel)
    hideStaticPrinterPaper(scene)

    if (!shouldShowInternalPrintLabel) {
      resetRoomPrintAnimations(actions)
      restoreRoomPrintIdlePose(scene, animations)
      activeAnimationRequestIdRef.current = null
    }

    trackHubInvalidate(
      shouldShowInternalPrintLabel
        ? 'roomModel.printLabelVisible'
        : 'roomModel.printLabelHidden',
    )
    invalidate()
  }, [actions, animations, currentRequest, invalidate, printStatus, scene])

  useEffect(() => {
    return () => {
      printLabelTextureRef.current?.dispose()
      printLabelTextureRef.current = null
    }
  }, [])

  useEffect(() => {
    if (
      !currentRequest ||
      printStatus !== 'printing' ||
      activeAnimationRequestIdRef.current === currentRequest.id
    ) {
      return
    }

    activeAnimationRequestIdRef.current = currentRequest.id

    ROOM_PRINT_ANIMATION_NAMES.forEach((animationName) => {
      const action = actions[animationName]
      if (!action) return

      action.setLoop(THREE.LoopOnce, 1)
      action.clampWhenFinished = true
      action.timeScale = animationName === 'print_head_up' ? -1 : 1
      action.reset()

      if (animationName === 'print_head_up') {
        action.time = action.getClip().duration
      }

      action.play()
    })

    trackHubInvalidate('roomModel.printAnimationStart')
    invalidate()
  }, [actions, currentRequest, invalidate, printStatus])
}
