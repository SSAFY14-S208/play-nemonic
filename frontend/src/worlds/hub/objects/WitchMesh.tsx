import { useMemo, useRef } from 'react'
import { useGLTF } from '@react-three/drei'
import { useFrame } from '@react-three/fiber'
import {
  Box3,
  CircleGeometry,
  Color,
  Group,
  MathUtils,
  Mesh,
  MeshBasicMaterial,
  MeshStandardMaterial,
  Object3D,
  Vector3,
  type Material,
} from 'three'
import { useHubViewStore } from '@/shared/stores'
import {
  HUB_WITCH_PLATFORM_HEIGHT,
  HUB_WITCH_PLATFORM_POSITION,
  HUB_WITCH_PLATFORM_ROTATION_Y,
} from '../constants'

const WITCH_MODEL_URL = '/models/mnemonic_witch_asset.glb'
const WITCH_SKIN_MESH_NAMES = new Set([
  'CHAR_head_round_peach',
  'CHAR_left_open_hand',
  'CHAR_neck_peach',
  'CHAR_nose_tiny_peach',
  'CHAR_right_table_hand',
])

const WITCH_TOUCH_MESH_NAME_PARTS = [
  'sleeve',
  'hand',
  'desk',
  'heart',
  'star',
  'glass',
]

function isMesh(object: Object3D): object is Mesh {
  return (object as Mesh).isMesh === true
}

function getMeshMaterials(mesh: Mesh): Material[] {
  if (!mesh.material) return []
  return Array.isArray(mesh.material) ? mesh.material : [mesh.material]
}

function createWitchShadow() {
  const shadow = new Mesh(
    new CircleGeometry(1, 64),
    new MeshBasicMaterial({
      color: '#6f4f89',
      transparent: true,
      opacity: 0.16,
      depthWrite: false,
    }),
  )

  shadow.name = 'PurpleWitchContactShadow'
  shadow.rotation.x = -Math.PI / 2
  shadow.position.y = 0.012
  shadow.scale.set(1.35, 0.78, 1)
  shadow.renderOrder = 19
  return shadow
}

function prepareWitchModel(source: Object3D) {
  const model = source.clone(true)
  const animatedMaterials: MeshStandardMaterial[] = []
  model.name = 'MnemonicWitchAsset'
  model.updateMatrixWorld(true)

  const bounds = new Box3().setFromObject(model)
  const size = new Vector3()
  const center = new Vector3()
  bounds.getSize(size)
  bounds.getCenter(center)

  const modelScale = HUB_WITCH_PLATFORM_HEIGHT / Math.max(size.x, size.y, size.z)
  model.scale.setScalar(modelScale)
  model.position.set(
    -center.x * modelScale,
    -bounds.min.y * modelScale,
    -center.z * modelScale,
  )

  model.traverse((child) => {
    if (!isMesh(child)) return

    child.castShadow = false
    child.receiveShadow = false
    child.material = Array.isArray(child.material)
      ? child.material.map((material) => material.clone())
      : child.material.clone()

    getMeshMaterials(child).forEach((material) => {
      const standardMaterial = material as MeshStandardMaterial
      standardMaterial.roughness = Math.max(standardMaterial.roughness ?? 0.82, 0.82)
      standardMaterial.metalness = Math.min(standardMaterial.metalness ?? 0, 0.12)

      if (WITCH_SKIN_MESH_NAMES.has(child.name) && standardMaterial.color) {
        standardMaterial.color.set('#ffc5a4')
        if (standardMaterial.emissive) {
          standardMaterial.emissive.set('#ffe0cc')
          standardMaterial.emissiveIntensity = 0.06
        }
      }

      if (
        WITCH_TOUCH_MESH_NAME_PARTS.some((namePart) => child.name.toLowerCase().includes(namePart))
        && standardMaterial.emissive
      ) {
        standardMaterial.userData.baseEmissiveIntensity =
          standardMaterial.emissiveIntensity ?? 0.08
        if (standardMaterial.emissive.getHex() === 0) {
          standardMaterial.emissive.set('#caa4ff')
        }
        animatedMaterials.push(standardMaterial)
      }
    })
  })

  return { model, animatedMaterials }
}

function createMagicStars() {
  return Array.from({ length: 16 }, (_, index) => {
    const angle = (index / 16) * Math.PI * 2
    const radius = 1.15 + (index % 4) * 0.11
    const height = 1.3 + (index % 5) * 0.24
    return {
      key: `witch-star-${index}`,
      position: [Math.cos(angle) * radius, height, Math.sin(angle) * radius] as [number, number, number],
      scale: 0.025 + (index % 3) * 0.008,
      phase: index * 0.67,
    }
  })
}

export default function WitchMesh() {
  const gltf = useGLTF(WITCH_MODEL_URL)
  const wrapperRef = useRef<Group>(null)
  const starGroupRef = useRef<Group>(null)
  const hoverProgressRef = useRef(0)
  const preparedWitch = useMemo(() => prepareWitchModel(gltf.scene), [gltf.scene])
  const { model, animatedMaterials } = preparedWitch
  const shadow = useMemo(() => createWitchShadow(), [])
  const magicStars = useMemo(() => createMagicStars(), [])
  const isWitchHovered = useHubViewStore((state) => state.isWitchHovered)
  const setWitchHovered = useHubViewStore((state) => state.setWitchHovered)
  const selectContent = useHubViewStore((state) => state.selectContent)

  useFrame(({ clock }, delta) => {
    const wrapper = wrapperRef.current
    if (!wrapper) return

    const elapsedTime = clock.elapsedTime
    hoverProgressRef.current = MathUtils.damp(
      hoverProgressRef.current,
      isWitchHovered ? 1 : 0,
      8,
      delta,
    )
    const hoverProgress = hoverProgressRef.current

    wrapper.rotation.y = HUB_WITCH_PLATFORM_ROTATION_Y
      + Math.sin(elapsedTime * 0.9) * 0.025
      + hoverProgress * 0.08
    wrapper.position.y = HUB_WITCH_PLATFORM_POSITION.y
      + Math.sin(elapsedTime * 1.4) * 0.025
      + hoverProgress * 0.035

    shadow.scale.set(1.35 + hoverProgress * 0.14, 0.78 + hoverProgress * 0.07, 1)

    if (starGroupRef.current) {
      starGroupRef.current.rotation.y = elapsedTime * (0.18 + hoverProgress * 0.22)
      starGroupRef.current.children.forEach((child, index) => {
        const pulse = (Math.sin(elapsedTime * 3.2 + index * 0.67) + 1) * 0.5
        child.scale.setScalar(1 + pulse * 0.35 + hoverProgress * 0.65)
      })
    }

    animatedMaterials.forEach((material) => {
      const baseEmissiveIntensity = material.userData.baseEmissiveIntensity
      if (typeof baseEmissiveIntensity !== 'number') return
      const pulse = Math.max(Math.sin(elapsedTime * 6.4), 0) * hoverProgress
      material.emissiveIntensity = baseEmissiveIntensity + pulse * 0.32
    })
  })

  return (
    <group
      ref={wrapperRef}
      position={[
        HUB_WITCH_PLATFORM_POSITION.x,
        HUB_WITCH_PLATFORM_POSITION.y,
        HUB_WITCH_PLATFORM_POSITION.z,
      ]}
      rotation={[0, HUB_WITCH_PLATFORM_ROTATION_Y, 0]}
    >
      <primitive object={shadow} />
      <pointLight position={[0, 2.25, 0.25]} color="#c7a7ff" intensity={0.9} distance={5} />
      <group ref={starGroupRef}>
        {magicStars.map((star) => (
          <mesh key={star.key} position={star.position} scale={star.scale}>
            <sphereGeometry args={[1, 10, 8]} />
            <meshBasicMaterial
              color={new Color().setHSL(0.72 + star.phase * 0.01, 0.76, 0.78)}
              transparent
              opacity={0.78}
              depthWrite={false}
            />
          </mesh>
        ))}
      </group>
      <mesh
        position={[0, 1.7, 0]}
        onPointerEnter={(event) => {
          event.stopPropagation()
          setWitchHovered(true)
        }}
        onPointerLeave={(event) => {
          event.stopPropagation()
          setWitchHovered(false)
        }}
        onClick={(event) => {
          event.stopPropagation()
          selectContent('fortune')
        }}
      >
        <sphereGeometry args={[1.35, 16, 10]} />
        <meshBasicMaterial transparent opacity={0} depthWrite={false} />
      </mesh>
      <primitive object={model} dispose={null} />
    </group>
  )
}

useGLTF.preload(WITCH_MODEL_URL)
