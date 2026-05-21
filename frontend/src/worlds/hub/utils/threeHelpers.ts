import {
  Mesh,
  type AnimationAction,
  type Material,
  type Object3D,
} from 'three'

export function isMesh(object: Object3D): object is Mesh {
  return (object as Mesh).isMesh === true
}

export function getMeshMaterials(mesh: Mesh): Material[] {
  if (!mesh.material) return []

  return Array.isArray(mesh.material) ? mesh.material : [mesh.material]
}

export function cloneMeshMaterials(mesh: Mesh) {
  if (!mesh.material) return

  mesh.material = Array.isArray(mesh.material)
    ? mesh.material.map((material) => material.clone())
    : mesh.material.clone()
}

export function disableMeshShadows(mesh: Mesh) {
  mesh.castShadow = false
  mesh.receiveShadow = false
}

export function disposeMesh(mesh: Mesh) {
  mesh.parent?.remove(mesh)
  mesh.geometry?.dispose()
  getMeshMaterials(mesh).forEach((material) => material.dispose?.())
}

export function isAnimationAction(
  action: AnimationAction | null,
): action is AnimationAction {
  return action !== null
}
