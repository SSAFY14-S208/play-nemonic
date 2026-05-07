import {
  Box3,
  Vector3,
  type Object3D,
} from 'three'

type ScaleReference = 'height' | 'horizontal' | 'maxDimension'

interface PrepareScaledGltfModelOptions {
  source: Object3D
  name: string
  targetSize: number
  baseY?: number
  clone?: (source: Object3D) => Object3D
  scaleReference?: ScaleReference
  onTraverse?: (child: Object3D) => void
}

function getReferenceSize(size: Vector3, scaleReference: ScaleReference) {
  if (scaleReference === 'height') {
    return size.y
  }

  if (scaleReference === 'horizontal') {
    return Math.max(size.x, size.z)
  }

  return Math.max(size.x, size.y, size.z)
}

export function prepareScaledGltfModel({
  source,
  name,
  targetSize,
  baseY = 0,
  clone = (object) => object.clone(true),
  scaleReference = 'maxDimension',
  onTraverse,
}: PrepareScaledGltfModelOptions) {
  const model = clone(source)
  model.name = name
  model.updateMatrixWorld(true)

  const bounds = new Box3().setFromObject(model)
  const size = new Vector3()
  const center = new Vector3()
  bounds.getSize(size)
  bounds.getCenter(center)

  const modelScale = targetSize / getReferenceSize(size, scaleReference)
  model.scale.setScalar(modelScale)
  model.position.set(
    -center.x * modelScale,
    baseY - bounds.min.y * modelScale,
    -center.z * modelScale,
  )

  if (onTraverse) {
    model.traverse(onTraverse)
  }

  return model
}
