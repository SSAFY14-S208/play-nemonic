import {
  preloadCenterNemonicModel,
  useCenterNemonicModel,
} from './hooks'

export default function CenterNemonicMesh() {
  const model = useCenterNemonicModel()

  return <primitive object={model} dispose={null} />
}

preloadCenterNemonicModel()
