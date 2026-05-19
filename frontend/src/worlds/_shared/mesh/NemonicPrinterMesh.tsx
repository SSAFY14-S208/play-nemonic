import { useEffect, useMemo, useRef } from 'react'
import type { MutableRefObject } from 'react'
import { useAnimations, useGLTF } from '@react-three/drei'
import { useThree } from '@react-three/fiber'
import type { ThreeElements, ThreeEvent } from '@react-three/fiber'
import { RigidBody } from '@react-three/rapier'
import * as THREE from 'three'
import type { AnimationAction } from 'three'
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

type NemonicPrinterMeshProps = ThreeElements['group'] & {
  actionsRef?: MutableRefObject<Record<string, AnimationAction | null>>
  baseColorOverride?: string
  baseColorOverrideMaterialNames?: readonly string[]
  highlightStrength?: 'default' | 'strong'
  modelScale?: number
  onPrintButtonClick?: () => void
  onOpenButtonClick?: () => void
  withPhysics?: boolean
}

export default function NemonicPrinterMesh({
  actionsRef,
  baseColorOverride,
  baseColorOverrideMaterialNames,
  highlightStrength = 'default',
  modelScale = 1,
  onPrintButtonClick,
  onOpenButtonClick,
  withPhysics = true,
  ...groupProps
}: NemonicPrinterMeshProps) {
  const groupRef = useRef<THREE.Group>(null)
  const materialOriginalsRef = useRef<
    Map<
      THREE.MeshStandardMaterial,
      {
        color: THREE.Color
        map: THREE.Texture | null
      }
    >
  >(new Map())
  const { gl } = useThree()
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
