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

const WALL_DETAIL_KEYWORDS = ['panel', 'wall', 'acoustic', 'room isometric']
const BRIGHT_ROOM_SURFACE_KEYWORDS = [
  'cube shelf',
  'main tabletop',
  'side tabletop',
  'shelf 1',
]
const CARPET_KEYWORDS = ['carpet', 'urso carpet']
const ROOM_GLASS_OBJECT_NAMES = ['case', 'glass panel', 'glass.001']

const KEEP_DOUBLE_SIDED_KEYWORDS = [
  'curtain',
  'flower',
  'glass',
  'leaf',
  'note',
  'panel',
  'paper',
  'plant',
  'room isometric',
  'screen',
  'tape',
]

const ROOM_MATERIAL_TUNING: Record<
  HubPerformanceMode,
  {
    brightSurfaceColorLerp: number
    brightSurfaceEmissiveIntensity: number
    disableBrightSurfaceMap: boolean
    brightSurfaceEnvMapIntensity: number
    brightSurfaceRoughness: number
    carpetColorLerp: number
    carpetEmissiveIntensity: number
    emissiveFallbackScale: number
    exportedEmissiveIntensityFloor: number
    maxEnvMapIntensity: number
    panelColorLerp: number
    panelEnvMapIntensity: number
    panelRoughness: number
    wallColorLerp: number
    wallEmissiveIntensity: number
  }
> = {
  diagnostic: {
    brightSurfaceColorLerp: 0.38,
    brightSurfaceEmissiveIntensity: 0,
    disableBrightSurfaceMap: false,
    brightSurfaceEnvMapIntensity: 0.16,
    brightSurfaceRoughness: 0.52,
    carpetColorLerp: 0.5,
    carpetEmissiveIntensity: 0,
    emissiveFallbackScale: 1,
    exportedEmissiveIntensityFloor: 0.46,
    maxEnvMapIntensity: 0.18,
    panelColorLerp: 0.14,
    panelEnvMapIntensity: 0.18,
    panelRoughness: 0.56,
    wallColorLerp: 0.28,
    wallEmissiveIntensity: 0,
  },
  balanced: {
    brightSurfaceColorLerp: 1,
    brightSurfaceEmissiveIntensity: 0.11,
    disableBrightSurfaceMap: true,
    brightSurfaceEnvMapIntensity: 0.52,
    brightSurfaceRoughness: 0.4,
    carpetColorLerp: 0.9,
    carpetEmissiveIntensity: 0.11,
    emissiveFallbackScale: 0.82,
    exportedEmissiveIntensityFloor: 0.34,
    maxEnvMapIntensity: 0.42,
    panelColorLerp: 0.22,
    panelEnvMapIntensity: 0.34,
    panelRoughness: 0.5,
    wallColorLerp: 0.72,
    wallEmissiveIntensity: 0,
  },
  quality: {
    brightSurfaceColorLerp: 0.94,
    brightSurfaceEmissiveIntensity: 0.14,
    disableBrightSurfaceMap: true,
    brightSurfaceEnvMapIntensity: 0.62,
    brightSurfaceRoughness: 0.38,
    carpetColorLerp: 0.82,
    carpetEmissiveIntensity: 0.13,
    emissiveFallbackScale: 0.9,
    exportedEmissiveIntensityFloor: 0.4,
    maxEnvMapIntensity: 0.55,
    panelColorLerp: 0.18,
    panelEnvMapIntensity: 0.42,
    panelRoughness: 0.46,
    wallColorLerp: 0.58,
    wallEmissiveIntensity: 0,
  },
}

const ROOM_PRINT_ANIMATION_NAMES = [
  'print_head_up',
  'print_button_click',
  'label_up',
] as const
const IDLE_POSE_ANIMATION_NAMES = ['print_head_up', 'label_up'] as const
const DISABLED_RAYCAST: THREE.Mesh['raycast'] = () => undefined
const ROOM_MATERIAL_BASELINE_KEY = 'nemonicRoomMaterialBaseline'

interface RoomMaterialBaseline {
  color: THREE.Color
  emissive: THREE.Color
  emissiveIntensity: number
  envMapIntensity: number
  metalness: number
  roughness: number
}

function includesAnyKeyword(value: string, keywords: string[]) {
  const normalizedValue = value.toLowerCase()

  return keywords.some((keyword) => normalizedValue.includes(keyword))
}

function getMeshMaterials(material: THREE.Material | THREE.Material[]) {
  return Array.isArray(material) ? material : [material]
}

function shouldUseMeshScopedMaterial(mesh: THREE.Mesh) {
  return includesAnyKeyword(mesh.name, [
    ...BRIGHT_ROOM_SURFACE_KEYWORDS,
    ...CARPET_KEYWORDS,
    ...WALL_DETAIL_KEYWORDS,
    ...ROOM_GLASS_OBJECT_NAMES,
  ])
}

function isRoomGlassSurface(mesh: THREE.Mesh) {
  return ROOM_GLASS_OBJECT_NAMES.includes(mesh.name.toLowerCase())
}

function cloneMeshMaterial(mesh: THREE.Mesh) {
  if (mesh.userData.nemonicRoomMaterialCloned) return

  mesh.material = Array.isArray(mesh.material)
    ? mesh.material.map((material) => material.clone())
    : mesh.material.clone()
  mesh.userData.nemonicRoomMaterialCloned = true
}

function getRoomMaterialBaseline(material: THREE.MeshStandardMaterial) {
  const existingBaseline = material.userData[
    ROOM_MATERIAL_BASELINE_KEY
  ] as RoomMaterialBaseline | undefined

  if (existingBaseline) return existingBaseline

  const baseline: RoomMaterialBaseline = {
    color: material.color.clone(),
    emissive: material.emissive.clone(),
    emissiveIntensity: material.emissiveIntensity,
    envMapIntensity: material.envMapIntensity,
    metalness: material.metalness,
    roughness: material.roughness,
  }

  material.userData[ROOM_MATERIAL_BASELINE_KEY] = baseline

  return baseline
}

function restoreRoomMaterialBaseline(material: THREE.MeshStandardMaterial) {
  const baseline = getRoomMaterialBaseline(material)

  material.color.copy(baseline.color)
  material.emissive.copy(baseline.emissive)
  material.emissiveIntensity = baseline.emissiveIntensity
  material.envMapIntensity = baseline.envMapIntensity
  material.metalness = baseline.metalness
  material.roughness = baseline.roughness
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
  const materialTuning = ROOM_MATERIAL_TUNING[performanceMode]
  const materialIdentity = `${child.name} ${material.name}`
  restoreRoomMaterialBaseline(material)

  const isEmissiveMesh = includesAnyKeyword(
    materialIdentity,
    EMISSIVE_MESH_KEYWORDS,
  )
  const hasExportedEmission =
    Boolean(material.emissiveMap) || material.emissive.getHex() !== 0

  if (isEmissiveMesh && (!hasExportedEmission || performanceMode === 'diagnostic')) {
    const materialName = material.name.toLowerCase()
    const objectName = child.name.toLowerCase()
    const isLogo = materialName.includes('logo') || objectName.includes('logo')
    const isScreen = materialName.includes('screen') || objectName.includes('screen')
    const isLed = materialName.includes('led') || objectName.includes('led')

    material.emissive = new THREE.Color(
      isLogo ? '#fff6ff' : isScreen || isLed ? '#bfefff' : '#d8c5ff',
    )
    material.emissiveIntensity = isLogo
      ? 2.1 * materialTuning.emissiveFallbackScale
      : isScreen || isLed
        ? 1.05 * materialTuning.emissiveFallbackScale
        : materialName.includes('red')
          ? 0.72 * materialTuning.emissiveFallbackScale
          : 0.46 * materialTuning.emissiveFallbackScale
  }

  if (isEmissiveMesh && hasExportedEmission && performanceMode !== 'diagnostic') {
    material.emissiveIntensity = Math.max(
      material.emissiveIntensity,
      materialTuning.exportedEmissiveIntensityFloor,
    )
  }

  const isCarpet = includesAnyKeyword(materialIdentity, CARPET_KEYWORDS)
  const isBrightRoomSurface = includesAnyKeyword(
    child.name,
    BRIGHT_ROOM_SURFACE_KEYWORDS,
  )
  const isWallDetail = includesAnyKeyword(materialIdentity, WALL_DETAIL_KEYWORDS)
  const isMainWallPanel =
    child.name.toLowerCase() === 'panel' ||
    material.name.toLowerCase().includes('main_wall_panel')
  const isGlassSurface = isRoomGlassSurface(child)

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

  if (material.opacity >= 1 && !isGlassSurface) {
    material.transparent = false
    material.depthWrite = true
  }

  if (includesAnyKeyword(materialIdentity, KEEP_DOUBLE_SIDED_KEYWORDS)) {
    material.side = THREE.DoubleSide
  }

  if (
    !performanceProfile.environment &&
    material.opacity >= 1 &&
    !includesAnyKeyword(materialIdentity, KEEP_DOUBLE_SIDED_KEYWORDS)
  ) {
    material.side = THREE.FrontSide
  }

  material.envMapIntensity = Math.min(
    material.envMapIntensity,
    materialTuning.maxEnvMapIntensity,
  )

  if (isGlassSurface) {
    material.color.lerp(new THREE.Color('#dff4ff'), 0.82)
    material.metalness = 0
    material.roughness = Math.min(material.roughness, 0.2)
    material.opacity = 0.42
    material.transparent = true
    material.depthWrite = false
    material.side = THREE.DoubleSide
    material.envMapIntensity = performanceMode === 'quality' ? 0.86 : 0.68

    if (material instanceof THREE.MeshPhysicalMaterial) {
      material.transmission = performanceProfile.environment ? 0.68 : 0
      material.thickness = 0.055
      material.ior = 1.36
    }
  }

  if (isBrightRoomSurface) {
    if (materialTuning.disableBrightSurfaceMap) {
      material.map = null
    }

    material.color.lerp(
      new THREE.Color('#fffaff'),
      materialTuning.brightSurfaceColorLerp,
    )
    material.metalness = 0
    material.roughness = Math.min(
      material.roughness,
      materialTuning.brightSurfaceRoughness,
    )
    material.emissive.lerp(new THREE.Color('#f8f1ff'), 1)
    material.emissiveIntensity = Math.max(
      material.emissiveIntensity,
      materialTuning.brightSurfaceEmissiveIntensity,
    )
    material.envMapIntensity = materialTuning.brightSurfaceEnvMapIntensity
  }

  if (isCarpet) {
    material.color.lerp(new THREE.Color('#fbf6ff'), materialTuning.carpetColorLerp)
    material.metalness = 0
    material.roughness = 1
    material.emissive.lerp(new THREE.Color('#f7efff'), 1)
    material.emissiveIntensity = Math.max(
      material.emissiveIntensity,
      materialTuning.carpetEmissiveIntensity,
    )
    material.envMapIntensity = Math.min(material.envMapIntensity, 0.08)
  }

  if (isWallDetail) {
    if (includesAnyKeyword(materialIdentity, ['room isometric'])) {
      material.map = null
    }

    if (isMainWallPanel) {
      material.color.lerp(new THREE.Color('#f0e9ff'), materialTuning.panelColorLerp)
      material.metalness = 0
      material.roughness = materialTuning.panelRoughness
      material.emissive.set('#000000')
      material.emissiveIntensity = 0
      material.envMapIntensity = Math.min(
        materialTuning.panelEnvMapIntensity,
        materialTuning.maxEnvMapIntensity,
      )
    } else {
      material.color.lerp(new THREE.Color('#f3ecff'), materialTuning.wallColorLerp)
      material.roughness = Math.max(material.roughness, 0.68)
      material.emissive.lerp(new THREE.Color('#f3ecff'), 1)
      material.emissiveIntensity = Math.max(
        material.emissiveIntensity,
        materialTuning.wallEmissiveIntensity,
      )
      material.envMapIntensity = Math.min(material.envMapIntensity, 0.28)
    }
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

  if (includesAnyKeyword(child.name, ['volumetric'])) {
    child.visible = false
    return
  }

  const shouldCastShadow =
    performanceProfile.shadows &&
    includesAnyKeyword(child.name, CAST_SHADOW_KEYWORDS)

  child.castShadow = shouldCastShadow
  child.receiveShadow = performanceProfile.shadows && shouldCastShadow

  if (shouldUseMeshScopedMaterial(child)) {
    cloneMeshMaterial(child)
  }

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
