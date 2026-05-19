import * as THREE from 'three'

export const ENABLED_MESH_RAYCAST = THREE.Mesh.prototype.raycast
export const DISABLED_RAYCAST: THREE.Mesh['raycast'] = () => undefined
