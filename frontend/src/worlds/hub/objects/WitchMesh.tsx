import { useMemo, useRef } from 'react'
import { useGLTF } from '@react-three/drei'
import { useFrame } from '@react-three/fiber'
import {
  Box3,
  CircleGeometry,
  Group,
  MathUtils,
  Mesh,
  MeshBasicMaterial,
  Object3D,
  Vector3,
} from 'three'
import { useHubViewStore } from '@/shared/stores'
import {
  HUB_WITCH_PLATFORM_HEIGHT,
  HUB_WITCH_PLATFORM_POSITION,
  HUB_WITCH_PLATFORM_ROTATION_Y,
} from '../constants'

const WITCH_MODEL_URL = '/models/mnemonic_witch_asset.glb'

function isMesh(object: Object3D): object is Mesh {
  return (object as Mesh).isMesh === true
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
    child.renderOrder = 30
  })

  return model
}

export default function WitchMesh() {
  const gltf = useGLTF(WITCH_MODEL_URL)
  const wrapperRef = useRef<Group>(null)
  const hoverProgressRef = useRef(0)
  const model = useMemo(() => prepareWitchModel(gltf.scene), [gltf.scene])
  const shadow = useMemo(() => createWitchShadow(), [])
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
      <mesh
        position={[0, 1.62, -0.08]}
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
        <boxGeometry args={[3.5, 3.55, 2.6]} />
        <meshBasicMaterial transparent opacity={0} depthWrite={false} colorWrite={false} />
      </mesh>
      <primitive object={model} dispose={null} />
    </group>
  )
}

useGLTF.preload(WITCH_MODEL_URL)
