import { useMemo } from 'react'
import { useGLTF } from '@react-three/drei'
import {
  Box3,
  CircleGeometry,
  Mesh,
  MeshBasicMaterial,
  Vector3,
  type Object3D,
} from 'three'
import { HUB_WITCH_PLATFORM_HEIGHT } from '../../constants'

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

export function useWitchModel() {
  const gltf = useGLTF(WITCH_MODEL_URL)
  const model = useMemo(() => prepareWitchModel(gltf.scene), [gltf.scene])
  const shadow = useMemo(() => createWitchShadow(), [])

  return { model, shadow }
}

export function preloadWitchModel() {
  useGLTF.preload(WITCH_MODEL_URL)
}
