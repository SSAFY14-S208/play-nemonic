export const HUB_CAMERA_HEIGHT = 2.65
export const HUB_CAMERA_FAR = 2200
export const HUB_CAMERA_FOV = 46
export const HUB_MODEL_ROOT_VERTICAL_OFFSET = -0.35
export const HUB_DECORATIVE_ASSET_LOAD_DELAY_MS = 120
export const HUB_NORMAL_ROTATION_EASE = 0.14
export const HUB_NORMAL_ZOOM_EASE = 0.18
export const HUB_VIEW_TRANSITION_ROTATION_EASE = 0.048
export const HUB_VIEW_TRANSITION_ZOOM_EASE = 0.058
export const HUB_SKY_DOME_RADIUS = 900
export const HUB_SKY_DOME_PARALLAX_FACTOR = 0.2
export const HUB_SKY_DOME_ROTATION_Y_OFFSET = 0
export const HUB_SKY_DOME_SCALE: [number, number, number] = [1.65, 1.65, 1.65]
export const HUB_SKY_BACKDROP_POSITION: [number, number, number] = [0, 30, -840]
export const HUB_SKY_BACKDROP_SIZE: [number, number] = [2600, 1500]
export const HUB_SKY_TEXTURE_OFFSET: [number, number] = [0.18, 0]
export const HUB_SKY_TEXTURE_REPEAT: [number, number] = [1, 1]

export const HUB_OUTER_BOARD_MESH_NAMES = new Set([
  'LowerBody',
  'BackLavender_1',
  'BackLavender_2',
  'LeftCyan_1',
  'LeftCyan_2',
  'FrontYellow_1',
  'FrontYellow_2',
  'RightGreen_1',
  'RightGreen_2',
])

export const HUB_ASSET_MATERIAL_STYLES: Record<
  string,
  { color: string; emissive: string; emissiveIntensity: number }
> = {
  BodyGray: { color: '#e7d6cf', emissive: '#f5ebe6', emissiveIntensity: 0.08 },
  RingSideCream: { color: '#f1ddca', emissive: '#fff5e6', emissiveIntensity: 0.08 },
  CenterDiskGray: { color: '#d9afb8', emissive: '#ffe5eb', emissiveIntensity: 0.12 },
  CubeLeft: { color: '#f4ebe1', emissive: '#fff7ef', emissiveIntensity: 0.1 },
  CubeRight: { color: '#fff4df', emissive: '#fff9ed', emissiveIntensity: 0.1 },
  CubeTopPink: { color: '#fff0e7', emissive: '#fff8f1', emissiveIntensity: 0.1 },
}

export const HUB_ASSET_MESH_STYLES: Record<
  string,
  { color: string; emissive: string; emissiveIntensity: number }
> = {
  BackLavender_1: { color: '#f5eadc', emissive: '#fff8ee', emissiveIntensity: 0.1 },
  LeftCyan_1: { color: '#f5eadc', emissive: '#fff8ee', emissiveIntensity: 0.1 },
  FrontYellow_1: { color: '#f5eadc', emissive: '#fff8ee', emissiveIntensity: 0.1 },
  RightGreen_1: { color: '#f5eadc', emissive: '#fff8ee', emissiveIntensity: 0.1 },
}

export const HUB_WATER_PLANE_SIZE = 1800
export const HUB_WATER_SURFACE_Y = 0.05
export const HUB_WATER_WAVE_AMPLITUDE = 0.18
export const HUB_WATER_WAVE_FREQUENCY = 0.045
export const HUB_WATER_WAVE_SPEED = 0.6

export const HUB_WITCH_PLATFORM_POSITION: [number, number, number] = [-6.05, 0.68, -0.64]
export const HUB_WITCH_PLATFORM_ROTATION_Y = -Math.PI / 2
export const HUB_WITCH_PLATFORM_HEIGHT = 3.25
export const HUB_CENTER_NEMONIC_BASE_Y = 0.84
export const HUB_CENTER_NEMONIC_MAX_SIZE = 2.36
export const HUB_COMMUNITY_CANVAS_BOOK_POSITION: [number, number, number] = [0.55, 0.76, -4.8]
export const HUB_COMMUNITY_CANVAS_BOOK_ROTATION_Y = Math.PI - 0.22
export const HUB_COMMUNITY_CANVAS_BOOK_MAX_SIZE = 5.625
export const HUB_FLIPBOOK_BUNNY_POSITION: [number, number, number] = [-2.6, 0.82, 4.35]
export const HUB_FLIPBOOK_BUNNY_ROTATION_Y = -Math.PI / 2
export const HUB_FLIPBOOK_BUNNY_MAX_SIZE = 4.2
export const HUB_FLIPBOOK_RUNNING_RABBIT_HEIGHT = 2.2
export const HUB_FLIPBOOK_RUNNING_RABBIT_SURFACE_Y = 0.42
export const HUB_FLIPBOOK_RUNNING_RABBIT_CENTER_X = 0
export const HUB_FLIPBOOK_RUNNING_RABBIT_CENTER_Z = 0
export const HUB_FLIPBOOK_RUNNING_RABBIT_ROTATION_Y = Math.PI / 2
export const HUB_RELAY_DRAWING_PATH = '/relay-drawing'
export const HUB_COMMUNITY_CANVAS_PATH = '/community-canvas'
export const HUB_FLIPBOOK_PATH = '/flipbook'
export const HUB_RELAY_DRAWING_PLACEHOLDER_POSITION: [number, number, number] = [5.15, 0.96, -0.35]
export const HUB_RELAY_DRAWING_PLACEHOLDER_ROTATION_Y = -0.34
export const HUB_RELAY_DRAWING_PLACEHOLDER_CUBES: Array<{
  name: string
  position: [number, number, number]
  scale: [number, number, number]
  color: string
}> = [
  {
    name: 'relay-drawing-cube-primary',
    position: [-0.72, 0.36, 0],
    scale: [0.82, 0.82, 0.82],
    color: '#fff4cc',
  },
  {
    name: 'relay-drawing-cube-secondary',
    position: [0.04, 0.55, -0.18],
    scale: [0.92, 0.92, 0.92],
    color: '#ffe49c',
  },
  {
    name: 'relay-drawing-cube-tertiary',
    position: [0.84, 0.42, 0.12],
    scale: [0.76, 0.76, 0.76],
    color: '#ffd870',
  },
]

export const HUB_CENTER_NEMONIC_PLACEHOLDER_MATERIAL_NAMES = new Set([
  'CubeLeft',
  'CubeRight',
  'CubeTopPink',
])

export const HUB_ORGANIC_BOARD_OUTLINE: Array<[number, number]> = [
  [-8.2, -4.8],
  [-5.9, -7.7],
  [-1.2, -8.3],
  [4.4, -7.6],
  [8.8, -4.8],
  [8.9, -0.6],
  [7.9, 1.7],
  [9.4, 3.9],
  [7.2, 6.3],
  [3.0, 7.4],
  [0.2, 8.3],
  [-3.5, 7.7],
  [-6.7, 6.0],
  [-8.7, 3.8],
  [-9.4, 0.2],
  [-8.6, -2.7],
]

export const HUB_ORGANIC_PLATE_SHAPES: Array<{
  name: string
  color: string
  points: Array<[number, number]>
}> = [
  {
    name: 'PaintBackGreen',
    color: '#96d27f',
    points: [
      [-6.4, -2.5],
      [-5.0, -7.1],
      [-0.8, -8.2],
      [4.9, -7.4],
      [8.7, -4.5],
      [8.1, -0.5],
      [3.4, 0.3],
      [-2.1, 0.1],
    ],
  },
  {
    name: 'PaintLeftPurple',
    color: '#a281d0',
    points: [
      [-7.4, -4.0],
      [-5.5, -5.4],
      [-2.6, -4.3],
      [-2.6, -1.1],
      [-2.0, 2.6],
      [-4.7, 5.4],
      [-7.9, 3.8],
      [-9.2, 0.3],
      [-8.5, -2.8],
    ],
  },
  {
    name: 'PaintFrontCoral',
    color: '#f58c97',
    points: [
      [-6.8, 3.2],
      [-2.6, 1.0],
      [1.0, 1.6],
      [3.3, 4.5],
      [2.2, 7.2],
      [-0.6, 8.2],
      [-3.9, 7.7],
      [-7.1, 5.8],
      [-8.5, 3.8],
    ],
  },
  {
    name: 'PaintRightBlue',
    color: '#aacfe8',
    points: [
      [1.6, 0.8],
      [5.1, 0.5],
      [8.2, 1.9],
      [9.3, 4.1],
      [7.1, 6.2],
      [3.3, 7.2],
      [1.4, 5.5],
      [0.8, 2.8],
    ],
  },
  {
    name: 'PaintBoothYellow',
    color: '#ffd56f',
    points: [
      [1.7, -2.0],
      [5.9, -3.7],
      [8.6, -1.5],
      [8.1, 1.8],
      [5.0, 2.7],
      [2.1, 1.1],
      [1.3, -0.5],
    ],
  },
]
