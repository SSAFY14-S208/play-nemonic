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
  HUB_CENTER_NEMONIC_PLACEHOLDER_MATERIAL_NAMES,
  HUB_ORGANIC_BOARD_OUTLINE,
  HUB_ORGANIC_PLATE_SHAPES,
  HUB_OUTER_BOARD_MESH_NAMES,
} from '../constants'
import CenterNemonicMesh from './CenterNemonicMesh'
import WitchMesh from './WitchMesh'

const PLATFORM_MODEL_URL = '/models/pastel_platform.glb'
const PLATFORM_TARGET_WIDTH = 8.9
const PLATFORM_BASE_Y = -0.8
interface PreparedPlatformModel {
  platform: Object3D
  position: [number, number, number]
  scale: number
}

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
      emissiveIntensity: 0.12,
      roughness: 0.95,
      metalness: 0,
      side: DoubleSide,
    }),
    new MeshStandardMaterial({
      color: sideColor,
      emissive: sideColor,
      emissiveIntensity: 0.08,
      roughness: 0.97,
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
    new CylinderGeometry(2.96, 3.06, 0.28, 160, 1, false),
    [
      new MeshStandardMaterial({
        color: '#d99baa',
        emissive: '#f3c6d0',
        emissiveIntensity: 0.08,
        roughness: 0.94,
        metalness: 0,
      }),
      new MeshStandardMaterial({
        color: '#efb5c1',
        emissive: '#f7cbd3',
        emissiveIntensity: 0.05,
        roughness: 0.9,
        metalness: 0,
      }),
      new MeshStandardMaterial({
        color: '#efb5c1',
        emissive: '#f7cbd3',
        emissiveIntensity: 0.05,
        roughness: 0.9,
        metalness: 0,
      }),
    ],
  )

  disk.name = 'RaisedCenterDisk'
  disk.position.y = 0.67
  disk.renderOrder = 20
  return disk
}

function isCenterNemonicPlaceholder(mesh: Mesh) {
  if (mesh.name === 'CenterCube') return true

  return getMeshMaterials(mesh).some((material) => (
    HUB_CENTER_NEMONIC_PLACEHOLDER_MATERIAL_NAMES.has(material.name)
  ))
}

function removeLoadedMesh(mesh: Mesh) {
  mesh.parent?.remove(mesh)
  mesh.geometry?.dispose()
  getMeshMaterials(mesh).forEach((material) => material.dispose?.())
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

function preparePlatformModel(source: Object3D): PreparedPlatformModel {
  const platform = source.clone(true)
  const bounds = new Box3().setFromObject(platform)
  const size = new Vector3()
  const center = new Vector3()
  bounds.getSize(size)
  bounds.getCenter(center)

  const maxDimension = Math.max(size.x, size.y, size.z)
  const modelScale = PLATFORM_TARGET_WIDTH / maxDimension

  const centerPlaceholderMeshes: Mesh[] = []
  platform.traverse((child) => {
    if (!isMesh(child)) return

    child.castShadow = false
    child.receiveShadow = false
    if (HUB_OUTER_BOARD_MESH_NAMES.has(child.name)) {
      child.visible = false
      return
    }
    if (child.name === 'CenterDisk') {
      child.visible = false
      return
    }
    if (isCenterNemonicPlaceholder(child)) {
      centerPlaceholderMeshes.push(child)
      return
    }

    applyPlatformPalette(child)
  })

  centerPlaceholderMeshes.forEach(removeLoadedMesh)
  platform.add(createOrganicBoardGroup())
  platform.add(createRaisedCenterDisk())
  return {
    platform,
    position: [-center.x, PLATFORM_BASE_Y, -center.z],
    scale: modelScale,
  }
}

export default function HubPlatformMesh() {
  const gltf = useGLTF(PLATFORM_MODEL_URL)
  const preparedPlatform = useMemo(() => preparePlatformModel(gltf.scene), [gltf.scene])

  return (
    <group position={preparedPlatform.position} scale={preparedPlatform.scale}>
      <primitive object={preparedPlatform.platform} dispose={null} />
      <CenterNemonicMesh />
      <WitchMesh />
    </group>
  )
}

useGLTF.preload(PLATFORM_MODEL_URL)
