import { useEffect, useMemo, useRef, type MutableRefObject } from 'react'
import { useAnimations, useGLTF } from '@react-three/drei'
import { useThree, type ThreeElements, type ThreeEvent } from '@react-three/fiber'
import { RigidBody } from '@react-three/rapier'
import * as THREE from 'three'
import { useButtonMeshHighlight } from '../hooks'

const MODEL_PATH = '/models/nemonic-printer.glb'
const BUTTON_MESH_NAMES = new Set([
  'NEMONIC_PRINT_BUTTON',
  'NEMONIC_OPEN_BUTTON',
])
const STRONG_BUTTON_HIGHLIGHT = {
  glowColor: new THREE.Color(0xa9e2e8),
  hoverIntensity: 0.9,
  idleMaxIntensity: 0.42,
  idleMinIntensity: 0.08,
  pulseSpeed: 2.8,
  tintMaxStrength: 0.08,
  tintMinStrength: 0,
} as const
const PRINT_LABEL_SHADER_CACHE_KEY = 'nemonic-print-label-thermal-ink-v3'

interface PrintLabelMaterialUserData {
  nemonicPrintMapUniform?: { value: THREE.Texture }
  nemonicPrintShaderPatched?: boolean
}

let fallbackPaperTexture: THREE.DataTexture | null = null

type NemonicPrinterMeshProps = ThreeElements['group'] & {
  actionsRef?: MutableRefObject<Record<string, THREE.AnimationAction | null>>
  baseColorOverride?: string
  baseColorOverrideMaterialNames?: readonly string[]
  highlightStrength?: 'default' | 'strong'
  modelScale?: number
  onPrintButtonClick?: () => void
  onOpenButtonClick?: () => void
  isPrintLabelVisible?: boolean
  printImageUrl?: string | null
  withPhysics?: boolean
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

function clonePrintLabelMaterial(mesh: THREE.Mesh) {
  if (mesh.userData.nemonicPrintLabelMaterialCloned) return

  mesh.material = Array.isArray(mesh.material)
    ? mesh.material.map((material) => material.clone())
    : mesh.material.clone()
  mesh.userData.nemonicPrintLabelMaterialCloned = true
}

function getFallbackPaperTexture() {
  if (fallbackPaperTexture) return fallbackPaperTexture

  fallbackPaperTexture = new THREE.DataTexture(
    new Uint8Array([255, 255, 255, 255]),
    1,
    1,
    THREE.RGBAFormat,
  )
  fallbackPaperTexture.colorSpace = THREE.SRGBColorSpace
  fallbackPaperTexture.needsUpdate = true
  return fallbackPaperTexture
}

function applyAlphaOverPaperShader(
  material: THREE.MeshStandardMaterial,
  printTexture: THREE.Texture,
) {
  const userData = material.userData as PrintLabelMaterialUserData

  if (!material.map) {
    material.map = getFallbackPaperTexture()
  }

  if (!userData.nemonicPrintMapUniform) {
    userData.nemonicPrintMapUniform = { value: printTexture }
  }
  userData.nemonicPrintMapUniform.value = printTexture
  const printMapUniform = userData.nemonicPrintMapUniform

  if (userData.nemonicPrintShaderPatched) {
    return
  }

  const previousOnBeforeCompile = material.onBeforeCompile

  material.onBeforeCompile = (shader, renderer) => {
    previousOnBeforeCompile.call(material, shader, renderer)

    shader.uniforms.nemonicPrintMap = printMapUniform
    shader.fragmentShader = shader.fragmentShader
      .replace(
        '#include <common>',
        '#include <common>\nuniform sampler2D nemonicPrintMap;',
      )
      .replace(
        '#include <map_fragment>',
        [
          '#include <map_fragment>',
          'vec4 nemonicPrintedColor = texture2D(nemonicPrintMap, vMapUv);',
          'float nemonicLuminance = dot(nemonicPrintedColor.rgb, vec3(0.299, 0.587, 0.114));',
          'float nemonicPaperMask = 1.0 - smoothstep(0.90, 0.985, nemonicLuminance);',
          'float nemonicInkDensity = pow(clamp(1.0 - nemonicLuminance, 0.0, 1.0), 1.65) * 0.78;',
          'float nemonicInkAlpha = nemonicPrintedColor.a * nemonicInkDensity;',
          'nemonicInkAlpha *= nemonicPaperMask;',
          'nemonicInkAlpha *= float(gl_FrontFacing);',
          'vec3 nemonicThermalInk = vec3(0.075, 0.073, 0.066);',
          'diffuseColor.rgb = mix(diffuseColor.rgb, nemonicThermalInk, nemonicInkAlpha);',
        ].join('\n'),
      )
  }

  material.customProgramCacheKey = () => PRINT_LABEL_SHADER_CACHE_KEY
  userData.nemonicPrintShaderPatched = true
  material.needsUpdate = true
}

function applyPrintLabelTexture(scene: THREE.Object3D, texture: THREE.Texture) {
  scene.traverse((child) => {
    if (!(child instanceof THREE.Mesh)) return
    if (!isInternalPrintLabelObject(child)) return

    clonePrintLabelMaterial(child)

    getMeshMaterials(child.material).forEach((material) => {
      if (!(material instanceof THREE.MeshStandardMaterial)) return

      material.metalness = 0
      material.roughness = Math.max(material.roughness, 0.68)
      material.side = THREE.DoubleSide
      material.toneMapped = false
      material.transparent = false
      material.alphaTest = 0
      material.depthWrite = true
      applyAlphaOverPaperShader(material, texture)
      material.needsUpdate = true
    })
  })
}

function setStaticPrinterPaperVisible(scene: THREE.Object3D, isVisible: boolean) {
  scene.traverse((child) => {
    if (!isStaticPrinterPaperObject(child)) return

    child.visible = isVisible
  })
}

function setInternalPrintLabelVisible(
  scene: THREE.Object3D,
  isVisible: boolean,
) {
  scene.traverse((child) => {
    if (!isInternalPrintLabelObject(child)) return

    child.visible = isVisible
  })
}

export default function NemonicPrinterMesh({
  actionsRef,
  baseColorOverride,
  baseColorOverrideMaterialNames,
  highlightStrength = 'default',
  modelScale = 1,
  onPrintButtonClick,
  onOpenButtonClick,
  isPrintLabelVisible,
  printImageUrl,
  withPhysics = true,
  ...groupProps
}: NemonicPrinterMeshProps) {
  const groupRef = useRef<THREE.Group>(null)
  const printLabelTextureRef = useRef<THREE.Texture | null>(null)
  const printLabelTextureSourceRef = useRef<string | null>(null)
  const materialOriginalsRef = useRef<
    Map<
      THREE.MeshStandardMaterial,
      {
        color: THREE.Color
        map: THREE.Texture | null
      }
    >
  >(new Map())
  const { gl, invalidate } = useThree()
  const { scene, animations } = useGLTF(MODEL_PATH)
  const { actions } = useAnimations(animations, groupRef)
  const { hoveredMeshRef } = useButtonMeshHighlight(
    scene,
    BUTTON_MESH_NAMES,
    highlightStrength === 'strong' ? STRONG_BUTTON_HIGHLIGHT : undefined,
  )
  const baseColorOverrideMaterialNameSet = useMemo(
    () =>
      baseColorOverrideMaterialNames
        ? new Set(baseColorOverrideMaterialNames)
        : null,
    [baseColorOverrideMaterialNames],
  )

  useEffect(() => {
    const maxAnisotropy = gl.capabilities.getMaxAnisotropy()
    const materialOriginals = materialOriginalsRef.current

    scene.traverse((child) => {
      if (!(child instanceof THREE.Mesh)) {
        return
      }

      child.castShadow = true
      child.receiveShadow = true

      const materials = Array.isArray(child.material)
        ? child.material
        : [child.material]

      materials.forEach((material) => {
        if (!(material instanceof THREE.MeshStandardMaterial)) {
          return
        }

        [
          material.map,
          material.normalMap,
          material.roughnessMap,
          material.metalnessMap,
        ].forEach((texture) => {
          if (!texture) {
            return
          }

          texture.anisotropy = maxAnisotropy
          texture.needsUpdate = true
        })

        const shouldOverrideBaseColor =
          Boolean(baseColorOverride) &&
          (!baseColorOverrideMaterialNameSet ||
            baseColorOverrideMaterialNameSet.has(material.name))

        if (shouldOverrideBaseColor) {
          if (!materialOriginals.has(material)) {
            materialOriginals.set(material, {
              color: material.color.clone(),
              map: material.map,
            })
          }

          if (baseColorOverride) {
            material.color.set(baseColorOverride)
          }

          material.needsUpdate = true
        }
      })
    })
    return () => {
      materialOriginals.forEach((original, material) => {
        material.color.copy(original.color)
        material.map = original.map
        material.needsUpdate = true
      })
      materialOriginals.clear()
    }
  }, [baseColorOverride, baseColorOverrideMaterialNameSet, scene, gl])

  useEffect(() => {
    if (actionsRef) {
      actionsRef.current = actions
    }
  }, [actions, actionsRef])

  useEffect(() => {
    if (printImageUrl === undefined && isPrintLabelVisible === undefined) return

    setInternalPrintLabelVisible(scene, Boolean(printImageUrl || isPrintLabelVisible))
    setStaticPrinterPaperVisible(scene, true)

    invalidate()

    return () => {
      setInternalPrintLabelVisible(scene, false)
      invalidate()
    }
  }, [invalidate, isPrintLabelVisible, printImageUrl, scene])

  useEffect(() => {
    if (printImageUrl === undefined) return

    if (!printImageUrl) {
      printLabelTextureSourceRef.current = null
      printLabelTextureRef.current?.dispose()
      printLabelTextureRef.current = null
      return
    }

    if (printLabelTextureSourceRef.current === printImageUrl) return

    printLabelTextureSourceRef.current = printImageUrl

    let isCancelled = false
    const textureLoader = new THREE.TextureLoader()
    textureLoader.setCrossOrigin('anonymous')
    const printLabelTexture = textureLoader.load(
      printImageUrl,
      (loadedTexture) => {
        if (isCancelled) {
          loadedTexture.dispose()
          return
        }

        loadedTexture.needsUpdate = true
        invalidate()
      },
    )

    printLabelTexture.colorSpace = THREE.SRGBColorSpace
    printLabelTexture.flipY = false
    printLabelTexture.needsUpdate = true

    printLabelTextureRef.current?.dispose()
    printLabelTextureRef.current = printLabelTexture

    applyPrintLabelTexture(scene, printLabelTexture)
    invalidate()

    return () => {
      isCancelled = true
    }
  }, [invalidate, printImageUrl, scene])

  useEffect(() => {
    return () => {
      printLabelTextureRef.current?.dispose()
      printLabelTextureRef.current = null
    }
  }, [])

  const handleClick = (event: ThreeEvent<MouseEvent>) => {
    event.stopPropagation()

    if (event.object.name === 'NEMONIC_PRINT_BUTTON') {
      onPrintButtonClick?.()
      return
    }

    if (event.object.name === 'NEMONIC_OPEN_BUTTON') {
      onOpenButtonClick?.()
    }
  }

  const handlePointerOver = (event: ThreeEvent<PointerEvent>) => {
    if (BUTTON_MESH_NAMES.has(event.object.name)) {
      hoveredMeshRef.current = event.object as THREE.Mesh
      if (typeof document !== 'undefined') {
        document.body.style.cursor = 'pointer'
      }
    }
  }

  const handlePointerOut = () => {
    hoveredMeshRef.current = null
    if (typeof document !== 'undefined') {
      document.body.style.cursor = ''
    }
  }

  const printerGroup = (
    <group
      ref={groupRef}
      {...groupProps}
      onClick={handleClick}
      onPointerOver={handlePointerOver}
      onPointerOut={handlePointerOut}
    >
      <primitive object={scene} scale={modelScale} />
    </group>
  )

  if (!withPhysics) {
    return printerGroup
  }

  return (
    <RigidBody type="fixed" colliders="hull">
      {printerGroup}
    </RigidBody>
  )
}

useGLTF.preload(MODEL_PATH)
