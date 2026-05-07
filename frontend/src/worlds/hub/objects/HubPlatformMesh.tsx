import type { Object3D } from 'three'
import { preloadHubPlatformModel } from './hooks'

interface HubPlatformMeshProps {
  platform: Object3D
}

export default function HubPlatformMesh({ platform }: HubPlatformMeshProps) {
  return <primitive object={platform} dispose={null} />
}

preloadHubPlatformModel()
