import { useMemo } from 'react'
import { useGLTF } from '@react-three/drei'
import {
  CircleGeometry,
  Mesh,
  MeshBasicMaterial,
} from 'three'
import { HUB_WITCH_PLATFORM_HEIGHT } from '../../constants'
import {
  disableMeshShadows,
  isMesh,
  prepareScaledGltfModel,
} from '../../utils'

const WITCH_MODEL_URL = '/models/mnemonic_witch_asset.glb'
const WITCH_SHADOW_SEGMENTS = 64
const WITCH_SHADOW_COLOR = '#6f4f89'
const WITCH_SHADOW_OPACITY = 0.16
const WITCH_SHADOW_POSITION_Y = 0.012
const WITCH_SHADOW_SCALE: [number, number, number] = [1.35, 0.78, 1]
const WITCH_SHADOW_RENDER_ORDER = 19

function createWitchShadow() {
  const shadow = new Mesh(
    new CircleGeometry(1, WITCH_SHADOW_SEGMENTS),
    new MeshBasicMaterial({
      color: WITCH_SHADOW_COLOR,
      transparent: true,
      opacity: WITCH_SHADOW_OPACITY,
      depthWrite: false,
    }),
  )

  shadow.name = 'PurpleWitchContactShadow'
  shadow.rotation.x = -Math.PI / 2
  shadow.position.y = WITCH_SHADOW_POSITION_Y
  shadow.scale.set(...WITCH_SHADOW_SCALE)
  shadow.renderOrder = WITCH_SHADOW_RENDER_ORDER
  return shadow
}

export function useWitchModel() {
  const gltf = useGLTF(WITCH_MODEL_URL)
  const model = useMemo(
    () => prepareScaledGltfModel({
      source: gltf.scene,
      name: 'MnemonicWitchAsset',
      targetSize: HUB_WITCH_PLATFORM_HEIGHT,
      onTraverse: (child) => {
        if (!isMesh(child)) return
        disableMeshShadows(child)
        child.renderOrder = 30
      },
    }),
    [gltf.scene],
  )
  const shadow = useMemo(() => createWitchShadow(), [])

  return { model, shadow }
}

export function preloadWitchModel() {
  useGLTF.preload(WITCH_MODEL_URL)
}
