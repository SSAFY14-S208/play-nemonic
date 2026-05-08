import {
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
} from 'three'
import {
  HUB_ORGANIC_BOARD_OUTLINE,
  HUB_ORGANIC_PLATE_SHAPES,
} from '../constants'

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

export function createOrganicBoardGroup() {
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

export function createRaisedCenterDisk() {
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
