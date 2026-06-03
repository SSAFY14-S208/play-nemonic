import { useEffect, useRef } from 'react'
import { useThree } from '@react-three/fiber'
import * as THREE from 'three'
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
  'plant',
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
const ROOM_GLASS_OBJECT_NAMES = ['glass panel']
const RETIRED_ROOM_PROP_OBJECT_NAMES = new Set([
  'cubetopshelf',
  'frame001',
  'largeframe',
  'peg',
  'picture001',
])
const RETIRED_ROOM_PROP_OBJECT_KEYWORDS = [
  'cubetopshelf',
  'largeframe',
]

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
    wallEnvMapIntensity: number
    wallRoughness: number
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
    wallEnvMapIntensity: 0.18,
    wallRoughness: 0.68,
  },
  balanced: {
    brightSurfaceColorLerp: 0.62,
    brightSurfaceEmissiveIntensity: 0.045,
    disableBrightSurfaceMap: true,
    brightSurfaceEnvMapIntensity: 0.42,
    brightSurfaceRoughness: 0.46,
    carpetColorLerp: 0.72,
    carpetEmissiveIntensity: 0.018,
    emissiveFallbackScale: 0.68,
    exportedEmissiveIntensityFloor: 0.34,
    maxEnvMapIntensity: 0.38,
    panelColorLerp: 0.26,
    panelEnvMapIntensity: 0.26,
    panelRoughness: 0.56,
    wallColorLerp: 0.45,
    wallEmissiveIntensity: 0.015,
    wallEnvMapIntensity: 0.28,
    wallRoughness: 0.64,
  },
  quality: {
    brightSurfaceColorLerp: 0.68,
    brightSurfaceEmissiveIntensity: 0.06,
    disableBrightSurfaceMap: true,
    brightSurfaceEnvMapIntensity: 0.5,
    brightSurfaceRoughness: 0.42,
    carpetColorLerp: 0.78,
    carpetEmissiveIntensity: 0.025,
    emissiveFallbackScale: 0.76,
    exportedEmissiveIntensityFloor: 0.4,
    maxEnvMapIntensity: 0.46,
    panelColorLerp: 0.3,
    panelEnvMapIntensity: 0.34,
    panelRoughness: 0.52,
    wallColorLerp: 0.5,
    wallEmissiveIntensity: 0.02,
    wallEnvMapIntensity: 0.34,
    wallRoughness: 0.6,
  },
}

const ROOM_PRINT_ANIMATION_NAMES = [
  'print_head_up',
  'print_button_click',
  'label_up',
] as const
const IDLE_POSE_ANIMATION_NAMES = ['print_head_up', 'label_up'] as const
const ENABLED_MESH_RAYCAST = THREE.Mesh.prototype.raycast
const DISABLED_RAYCAST: THREE.Mesh['raycast'] = () => undefined
const ROOM_MATERIAL_BASELINE_KEY = 'nemonicRoomMaterialBaseline'
const COMMUNITY_CANVAS_WHITEBOARD_MESH_NAMES = new Set([
  'CommunityCanvasWhiteboard',
  'CommunityCanvasWhiteboardOutline',
  'CommunityCanvasWhiteboarOutline',
])

interface RoomMaterialBaseline {
  color: THREE.Color
  emissive: THREE.Color
  emissiveMap: THREE.Texture | null
  emissiveIntensity: number
  envMapIntensity: number
  depthWrite: boolean
  map: THREE.Texture | null
  metalnessMap: THREE.Texture | null
  metalness: number
  normalMap: THREE.Texture | null
  opacity: number
  roughness: number
  roughnessMap: THREE.Texture | null
  side: THREE.Side
  toneMapped: boolean
  transparent: boolean
  clearcoat: number | null
  clearcoatRoughness: number | null
  transmission: number | null
  thickness: number | null
  ior: number | null
}

function includesAnyKeyword(value: string, keywords: string[]) {
  const normalizedValue = value.toLowerCase()

  return keywords.some((keyword) => normalizedValue.includes(keyword))
}

export function isCommunityCanvasWhiteboardMesh(
  object: THREE.Object3D,
): object is THREE.Mesh {
  return (
    object instanceof THREE.Mesh &&
    COMMUNITY_CANVAS_WHITEBOARD_MESH_NAMES.has(object.name)
  )
}

function normalizeRoomIdentifier(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9가-힣]/g, '')
}

function getMeshMaterials(material: THREE.Material | THREE.Material[]) {
  return Array.isArray(material) ? material : [material]
}

function isRetiredRoomPropMesh(mesh: THREE.Mesh) {
  const objectName = normalizeRoomIdentifier(mesh.name)

  return (
    RETIRED_ROOM_PROP_OBJECT_NAMES.has(objectName) ||
    RETIRED_ROOM_PROP_OBJECT_KEYWORDS.some((keyword) =>
      objectName.includes(keyword),
    )
  )
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

function createBrightPhysicalMaterial(material: THREE.MeshStandardMaterial) {
  if (material instanceof THREE.MeshPhysicalMaterial) return material

  const physicalMaterial = new THREE.MeshPhysicalMaterial()

  physicalMaterial.copy(material as THREE.MeshPhysicalMaterial)
  physicalMaterial.name = material.name
  physicalMaterial.userData = { ...material.userData }
  physicalMaterial.defines = { ...material.defines }
  material.dispose()

  return physicalMaterial
}

function upgradeBrightSurfaceMaterial(mesh: THREE.Mesh) {
  if (mesh.userData.nemonicBrightSurfaceMaterialUpgraded) return
  if (!includesAnyKeyword(mesh.name, BRIGHT_ROOM_SURFACE_KEYWORDS)) return

  mesh.material = Array.isArray(mesh.material)
    ? mesh.material.map((material) =>
        material instanceof THREE.MeshStandardMaterial
          ? createBrightPhysicalMaterial(material)
          : material,
      )
    : mesh.material instanceof THREE.MeshStandardMaterial
      ? createBrightPhysicalMaterial(mesh.material)
      : mesh.material

  mesh.userData.nemonicBrightSurfaceMaterialUpgraded = true
}

function getRoomMaterialBaseline(material: THREE.MeshStandardMaterial) {
  const existingBaseline = material.userData[
    ROOM_MATERIAL_BASELINE_KEY
  ] as RoomMaterialBaseline | undefined

  if (existingBaseline) return existingBaseline

  const baseline: RoomMaterialBaseline = {
    color: material.color.clone(),
    emissive: material.emissive.clone(),
    emissiveMap: material.emissiveMap,
    emissiveIntensity: material.emissiveIntensity,
    envMapIntensity: material.envMapIntensity,
    depthWrite: material.depthWrite,
    map: material.map,
    metalnessMap: material.metalnessMap,
    metalness: material.metalness,
    normalMap: material.normalMap,
    opacity: material.opacity,
    roughness: material.roughness,
    roughnessMap: material.roughnessMap,
    side: material.side,
    toneMapped: material.toneMapped,
    transparent: material.transparent,
    clearcoat:
      material instanceof THREE.MeshPhysicalMaterial ? material.clearcoat : null,
    clearcoatRoughness:
      material instanceof THREE.MeshPhysicalMaterial
        ? material.clearcoatRoughness
        : null,
    transmission:
      material instanceof THREE.MeshPhysicalMaterial ? material.transmission : null,
    thickness:
      material instanceof THREE.MeshPhysicalMaterial ? material.thickness : null,
    ior: material instanceof THREE.MeshPhysicalMaterial ? material.ior : null,
  }

  material.userData[ROOM_MATERIAL_BASELINE_KEY] = baseline

  return baseline
}

function restoreRoomMaterialBaseline(material: THREE.MeshStandardMaterial) {
  const baseline = getRoomMaterialBaseline(material)

  material.color.copy(baseline.color)
  material.emissive.copy(baseline.emissive)
  material.emissiveMap = baseline.emissiveMap
  material.emissiveIntensity = baseline.emissiveIntensity
  material.envMapIntensity = baseline.envMapIntensity
  material.depthWrite = baseline.depthWrite
  material.map = baseline.map
  material.metalnessMap = baseline.metalnessMap
  material.metalness = baseline.metalness
  material.normalMap = baseline.normalMap
  material.opacity = baseline.opacity
  material.roughness = baseline.roughness
  material.roughnessMap = baseline.roughnessMap
  material.side = baseline.side
  material.toneMapped = baseline.toneMapped
  material.transparent = baseline.transparent

  if (material instanceof THREE.MeshPhysicalMaterial) {
    material.clearcoat = baseline.clearcoat ?? material.clearcoat
    material.clearcoatRoughness =
      baseline.clearcoatRoughness ?? material.clearcoatRoughness
    material.transmission = baseline.transmission ?? material.transmission
    material.thickness = baseline.thickness ?? material.thickness
    material.ior = baseline.ior ?? material.ior
  }
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
  actions: Record<string, THREE.AnimationAction | null>,
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

  if (isBrightRoomSurface || isCarpet || (isWallDetail && !isGlassSurface)) {
    material.opacity = 1
    material.transparent = false
    material.depthWrite = true
  }

  material.envMapIntensity = Math.min(
    material.envMapIntensity,
    materialTuning.maxEnvMapIntensity,
  )

  if (isGlassSurface) {
    material.color.lerp(new THREE.Color('#d7f0ff'), 0.88)
    material.emissive.set('#6f8ed0')
    material.emissiveIntensity = performanceMode === 'quality' ? 0.055 : 0.035
    material.emissiveMap = null
    material.map = null
    material.metalness = 0
    material.roughness = Math.min(material.roughness, 0.22)
    material.opacity = performanceMode === 'quality' ? 0.42 : 0.46
    material.transparent = true
    material.depthWrite = false
    material.side = THREE.DoubleSide
    material.toneMapped = true
    material.envMapIntensity = performanceMode === 'quality' ? 0.96 : 0.76

    if (material instanceof THREE.MeshPhysicalMaterial) {
      material.transmission = performanceProfile.environment ? 0.66 : 0
      material.thickness = 0.038
      material.ior = 1.36
    }
  }

  if (isBrightRoomSurface) {
    if (materialTuning.disableBrightSurfaceMap) {
      material.map = null
    }

    material.color.lerp(
      new THREE.Color('#f6f1fa'),
      materialTuning.brightSurfaceColorLerp,
    )
    material.metalness = 0
    material.roughness = Math.min(
      material.roughness,
      materialTuning.brightSurfaceRoughness,
    )
    material.emissive.lerp(new THREE.Color('#eadcf5'), 1)
    material.emissiveIntensity = Math.max(
      material.emissiveIntensity,
      materialTuning.brightSurfaceEmissiveIntensity,
    )
    material.envMapIntensity = materialTuning.brightSurfaceEnvMapIntensity
    material.toneMapped = true

    if (material instanceof THREE.MeshPhysicalMaterial) {
      material.clearcoat = performanceMode === 'quality' ? 0.54 : 0.42
      material.clearcoatRoughness = performanceMode === 'quality' ? 0.26 : 0.3
      material.ior = 1.45
    }
  }

  if (isCarpet) {
    material.color.lerp(new THREE.Color('#f1e7f1'), materialTuning.carpetColorLerp)
    material.metalness = 0
    material.roughness = 1
    material.emissive.lerp(new THREE.Color('#ead8ee'), 1)
    material.emissiveIntensity = Math.max(
      material.emissiveIntensity,
      materialTuning.carpetEmissiveIntensity,
    )
    material.envMapIntensity = Math.min(material.envMapIntensity, 0.08)
  }

  if (isWallDetail && !isGlassSurface) {
    if (includesAnyKeyword(materialIdentity, ['room isometric'])) {
      material.map = null
    }

    if (isMainWallPanel) {
      material.color.lerp(new THREE.Color('#f1eaf9'), materialTuning.panelColorLerp)
      material.metalness = 0
      material.roughness = materialTuning.panelRoughness
      material.emissive.set('#000000')
      material.emissiveIntensity = 0
      material.envMapIntensity = Math.min(
        materialTuning.panelEnvMapIntensity,
        materialTuning.maxEnvMapIntensity,
      )
    } else {
      material.color.lerp(new THREE.Color('#f3edf7'), materialTuning.wallColorLerp)
      material.roughness = Math.min(material.roughness, materialTuning.wallRoughness)
      material.emissive.lerp(new THREE.Color('#eadff2'), 1)
      material.emissiveIntensity = Math.max(
        material.emissiveIntensity,
        materialTuning.wallEmissiveIntensity,
      )
      material.envMapIntensity = Math.min(
        materialTuning.wallEnvMapIntensity,
        materialTuning.maxEnvMapIntensity,
      )
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
  const isGlassSurface = isRoomGlassSurface(child)

  child.raycast = DISABLED_RAYCAST

  if (isRetiredRoomPropMesh(child)) {
    child.visible = false
    child.castShadow = false
    child.receiveShadow = false
    return
  }

  if (includesAnyKeyword(child.name, ['volumetric'])) {
    child.visible = false
    return
  }

  if (isCommunityCanvasWhiteboardMesh(child)) {
    child.raycast = ENABLED_MESH_RAYCAST
    child.userData.hubNavigation = 'community-canvas-whiteboard'
  }

  const shouldCastShadow =
    performanceProfile.shadows &&
    !isGlassSurface &&
    includesAnyKeyword(child.name, CAST_SHADOW_KEYWORDS)

  child.castShadow = shouldCastShadow
  child.receiveShadow = performanceProfile.shadows && shouldCastShadow

  if (shouldUseMeshScopedMaterial(child)) {
    cloneMeshMaterial(child)
  }
  upgradeBrightSurfaceMaterial(child)

  getMeshMaterials(child.material).forEach((material) => {
    if (!(material instanceof THREE.MeshStandardMaterial)) return

    configureRoomMaterial(child, material, maxAnisotropy, performanceMode)
  })
}

export function useRoomModel(
  scene: THREE.Object3D,
  animations: THREE.AnimationClip[],
  actions: Record<string, THREE.AnimationAction | null>,
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
