import type { HubFocusKey } from '@/shared/types'

export type RoomPreviewVariant = 'preview' | 'hub'

export const ROOM_PREVIEW_MODEL_PATH =
  '/models/isometric-girl-room-stage6-web.glb?v=20260518-stage6-clean-wall-light-rig'

export const ROOM_PREVIEW_SCALE = 4.2

export const ROOM_PREVIEW_MODEL_SOURCE_BOUNDS = {
  center: [0.260101, 0.692556, 0.085635],
  min: [-0.671837, -0.004865, -0.812364],
} as const

export const ROOM_PREVIEW_MODEL_OFFSET = [
  -ROOM_PREVIEW_MODEL_SOURCE_BOUNDS.center[0] * ROOM_PREVIEW_SCALE,
  -ROOM_PREVIEW_MODEL_SOURCE_BOUNDS.min[1] * ROOM_PREVIEW_SCALE,
  -ROOM_PREVIEW_MODEL_SOURCE_BOUNDS.center[2] * ROOM_PREVIEW_SCALE,
] as [number, number, number]

export const ROOM_PREVIEW_CAMERA = {
  far: 80,
  fov: 42,
  near: 0.1,
  position: [4.7, 2.45, 5.15] as [number, number, number],
  target: [-0.72, 1.32, -1.45] as [number, number, number],
}

export const ROOM_PREVIEW_HUB_CAMERA_PRESETS: Record<
  HubFocusKey,
  {
    position: [number, number, number]
    target: [number, number, number]
  }
> = {
  overview: {
    position: [1.08, 3.5, 2.06],
    target: [-2.48, 1.9, -2.1],
  },
  mainDesk: {
    position: [1.08, 3.5, 2.06],
    target: [-2.48, 1.9, -2.1],
  },
  monitor: {
    position: [-1.99, 2.03, -0.58],
    target: [-1.99, 2.03, -3.06],
  },
  workspace: {
    position: [1.08, 3.5, 2.06],
    target: [-2.48, 1.9, -2.1],
  },
  printer: {
    position: [-2.39, 1.79, -1.6],
    target: [-2.83, 1.46, -2.48],
  },
  pegboard: {
    position: [1.08, 3.5, 2.06],
    target: [-2.48, 1.9, -2.1],
  },
  communityBoard: {
    position: [-1.72, 2.35, -0.82],
    target: [-3.79, 2.35, -0.82],
  },
}

export const ROOM_PREVIEW_HUB_CAMERA_LIMITS = {
  boundary: {
    max: [3.72, 5.05, 4.02] as [number, number, number],
    min: [-3.5, 0.62, -3.42] as [number, number, number],
  },
  boundaryFriction: 0.08,
  maxAzimuthAngle: Math.PI * 0.52,
  maxDistance: 6.75,
  maxPolarAngle: Math.PI * 0.5,
  minAzimuthAngle: -Math.PI * 0.04,
  minDistance: 0.95,
  minPolarAngle: Math.PI * 0.17,
}

export const ROOM_PREVIEW_RENDERING = {
  backgroundColor: '#f4edf6',
  devicePixelRatio: [1, 1.75] as [number, number],
  fogFar: 28,
  fogNear: 13,
  postProcessing: {
    bloomIntensity: 0.2,
    bloomRadius: 0.36,
    luminanceSmoothing: 0.42,
    luminanceThreshold: 0.72,
    resolutionScale: 0.82,
  },
  toneMappingExposure: 1.12,
}

export const ROOM_PREVIEW_RENDERING_PROFILES: Record<
  RoomPreviewVariant,
  {
    devicePixelRatio: number | [number, number]
    postProcessing: boolean
    shadows: boolean
  }
> = {
  preview: {
    devicePixelRatio: ROOM_PREVIEW_RENDERING.devicePixelRatio,
    postProcessing: true,
    shadows: true,
  },
  hub: {
    devicePixelRatio: [1, 1.2],
    postProcessing: true,
    shadows: false,
  },
}

export const ROOM_PREVIEW_LIGHTING = {
  ambient: {
    color: '#fff7ff',
    intensity: 0.52,
  },
  environment: {
    intensity: 0.2,
    preset: 'apartment' as const,
  },
  hemisphere: {
    groundColor: '#b8d9ff',
    intensity: 0.42,
    skyColor: '#ffe9fb',
  },
}

export const ROOM_PREVIEW_CONTROLS = {
  dampingFactor: 0.08,
  maxDistance: 12,
  maxPolarAngle: Math.PI / 2.08,
  minDistance: 3.4,
}

export const ROOM_PREVIEW_MATERIALS = {
  defaultEnvironmentIntensity: 0.4,
  emissiveIntensityFloor: 1.25,
  maxEnvironmentIntensity: 0.68,
  minEnvironmentIntensity: 0.32,
  tableLightStripColor: '#fff5ff',
  tableLightStripEmissiveIntensity: 2.8,
  textureAnisotropy: 8,
}

export const HIDDEN_PREVIEW_OBJECT_KEYWORDS = [
  'room isometric',
  'room volumetric',
]
