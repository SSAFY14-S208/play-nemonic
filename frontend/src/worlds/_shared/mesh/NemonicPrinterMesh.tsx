import { useEffect, useRef } from 'react'
import type { MutableRefObject } from 'react'
import { useAnimations, useGLTF } from '@react-three/drei'
import { useThree } from '@react-three/fiber'
import type { ThreeElements, ThreeEvent } from '@react-three/fiber'
import { RigidBody } from '@react-three/rapier'
import * as THREE from 'three'
import type { AnimationAction } from 'three'

const MODEL_PATH = '/models/nemonic-printer.glb'
const BUTTON_MESH_NAMES = new Set([
  'NEMONIC_PRINT_BUTTON',
  'NEMONIC_OPEN_BUTTON',
])

type NemonicPrinterMeshProps = ThreeElements['group'] & {
  actionsRef?: MutableRefObject<Record<string, AnimationAction | null>>
  modelScale?: number
  onPrintButtonClick?: () => void
  onOpenButtonClick?: () => void
  withPhysics?: boolean
}

export default function NemonicPrinterMesh({
  actionsRef,
  modelScale = 1,
  onPrintButtonClick,
  onOpenButtonClick,
  withPhysics = true,
  ...groupProps
}: NemonicPrinterMeshProps) {
  const groupRef = useRef<THREE.Group>(null)
  const { gl } = useThree()
  const { scene, animations } = useGLTF(MODEL_PATH)
  const { actions } = useAnimations(animations, groupRef)

  useEffect(() => {
    const maxAnisotropy = gl.capabilities.getMaxAnisotropy()

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
      })
    })
  }, [scene, gl])

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
    if (typeof document !== 'undefined' && BUTTON_MESH_NAMES.has(event.object.name)) {
      document.body.style.cursor = 'pointer'
    }
  }

  const handlePointerOut = () => {
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
