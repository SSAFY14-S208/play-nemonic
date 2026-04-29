import { useMemo } from 'react'
import { useGLTF } from '@react-three/drei'
import {
  Box3,
  CatmullRomCurve3,
  Color,
  CylinderGeometry,
  DoubleSide,
  ExtrudeGeometry,
  Group,
  Mesh,
  MeshStandardMaterial,
  Shape,
  Vector2,
  Vector3,
  type Material,
  type Object3D,
} from 'three'
import {
  HUB_ASSET_MATERIAL_STYLES,
  HUB_ASSET_MESH_STYLES,
  HUB_ORGANIC_BOARD_OUTLINE,
  HUB_ORGANIC_PLATE_SHAPES,
  HUB_OUTER_BOARD_MESH_NAMES,
} from '../constants'
import WitchMesh from './WitchMesh'

const PLATFORM_MODEL_URL = '/models/pastel_platform.glb'
const PLATFORM_TARGET_WIDTH = 8.9
const PLATFORM_BASE_Y = -0.8

function isMesh(object: Object3D): object is Mesh {
  return (object as Mesh).isMesh === true
}

function getMeshMaterials(mesh: Mesh): Material[] {
  if (!mesh.material) return []
  return Array.isArray(mesh.material) ? mesh.material : [mesh.material]
}

function createSmoothShape(
  points: Array<[number, number]>,
  tension = 0.48,
  divisions = 18,
) {
  const curve = new CatmullRomCurve3(
    points.map(([pointX, pointZ]) => new Vector3(pointX, pointZ, 0)),
    true,
    'catmullrom',
    tension,
  )

  return new Shape(
    curve
      .getPoints(points.length * divisions)
      .map((point) => new Vector2(point.x, point.y)),
  )
}

function createPatchMaterials(color: string) {
  const sideColor = new Color(color).multiplyScalar(0.76)

  return [
    new MeshStandardMaterial({
      color,
      emissive: color,
      emissiveIntensity: 0.06,
      roughness: 0.94,
      metalness: 0,
      side: DoubleSide,
    }),
    new MeshStandardMaterial({
      color: sideColor,
      emissive: sideColor,
      emissiveIntensity: 0.035,
      roughness: 0.96,
      metalness: 0,
    }),
  ]
}

function createOrganicBoardGroup() {
  const group = new Group()
  group.name = 'OrganicBoard'

  const baseInset = 0.92
  const baseShape = createSmoothShape(
    HUB_ORGANIC_BOARD_OUTLINE.map(([pointX, pointZ]) => [
      pointX * baseInset,
      pointZ * baseInset,
    ]),
    0.42,
    20,
  )

  const baseMesh = new Mesh(
    new ExtrudeGeometry(baseShape, {
      depth: 0.5,
      bevelEnabled: true,
      bevelSize: 0.12,
      bevelThickness: 0.08,
      bevelSegments: 8,
      curveSegments: 16,
    }),
    [
      new MeshStandardMaterial({
        color: '#fff4df',
        emissive: '#fff7ec',
        emissiveIntensity: 0.08,
        roughness: 0.94,
        metalness: 0,
        transparent: true,
        opacity: 0.54,
      }),
      new MeshStandardMaterial({
        color: '#d2bca2',
        emissive: '#f1dfc8',
        emissiveIntensity: 0.05,
        roughness: 0.96,
        metalness: 0,
      }),
    ],
  )

  baseMesh.name = 'OrganicBoardBase'
  baseMesh.rotation.x = Math.PI / 2
  baseMesh.position.y = 0.43
  baseMesh.renderOrder = 0
  group.add(baseMesh)

  HUB_ORGANIC_PLATE_SHAPES.forEach(({ name, color, points }, plateIndex) => {
    const shape = createSmoothShape(points, 0.48, 18)
    const mesh = new Mesh(
      new ExtrudeGeometry(shape, {
        depth: 0.16,
        bevelEnabled: true,
        bevelSize: 0.035,
        bevelThickness: 0.03,
        bevelSegments: 5,
        curveSegments: 12,
      }),
      createPatchMaterials(color),
    )

    mesh.name = name
    mesh.rotation.x = Math.PI / 2
    mesh.position.y = 0.545 + plateIndex * 0.003
    mesh.renderOrder = plateIndex + 1
    group.add(mesh)
  })

  return group
}

function createRaisedCenterDisk() {
  const disk = new Mesh(
    new CylinderGeometry(1.28, 1.48, 0.18, 96),
    new MeshStandardMaterial({
      color: '#f4d6dd',
      emissive: '#ffe7ed',
      emissiveIntensity: 0.08,
      roughness: 0.94,
      metalness: 0,
      transparent: true,
      opacity: 0.58,
    }),
  )

  disk.name = 'SoftRaisedCenterDisk'
  disk.position.y = 0.56
  disk.renderOrder = 6
  return disk
}

function applyPlatformPalette(mesh: Mesh) {
  const sourceMaterial = getMeshMaterials(mesh)[0]
  if (!sourceMaterial) return

  const material = sourceMaterial.clone() as MeshStandardMaterial
  const style = HUB_ASSET_MESH_STYLES[mesh.name]
    ?? HUB_ASSET_MATERIAL_STYLES[sourceMaterial.name]

  if (style) {
    material.color.set(style.color)
    if (material.emissive) {
      material.emissive.set(style.emissive)
      material.emissiveIntensity = style.emissiveIntensity
    }
  }

  material.roughness = 0.92
  material.metalness = 0
  mesh.material = material
}

function preparePlatformModel(source: Object3D) {
  const platform = source.clone(true)
  const bounds = new Box3().setFromObject(platform)
  const size = new Vector3()
  const center = new Vector3()
  bounds.getSize(size)
  bounds.getCenter(center)

  platform.position.sub(center)
  platform.position.y = PLATFORM_BASE_Y

  const maxDimension = Math.max(size.x, size.y, size.z)
  const modelScale = PLATFORM_TARGET_WIDTH / maxDimension
  platform.scale.setScalar(modelScale)

  platform.traverse((child) => {
    if (!isMesh(child)) return

    child.castShadow = false
    child.receiveShadow = false
    if (HUB_OUTER_BOARD_MESH_NAMES.has(child.name)) {
      child.visible = false
      return
    }

    applyPlatformPalette(child)
  })

  platform.add(createOrganicBoardGroup())
  platform.add(createRaisedCenterDisk())
  return platform
}

export default function HubPlatformMesh() {
  const gltf = useGLTF(PLATFORM_MODEL_URL)
  const platform = useMemo(() => preparePlatformModel(gltf.scene), [gltf.scene])

  return (
    <group>
      <primitive object={platform} dispose={null} />
      <WitchMesh />
    </group>
  )
}

useGLTF.preload(PLATFORM_MODEL_URL)
