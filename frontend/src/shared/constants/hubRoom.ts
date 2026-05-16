import type {
  HubFocusKey,
  HubPerformanceMode,
  HubPerformanceProfile,
} from '@/shared/types'

export const HUB_ROOM_MODEL_PATH = '/models/isometric-girl-room.glb?v=file1-whiteboard-kept-20260516'
export const HUB_ROOM_MODEL_INCLUDES_PRINTER = true

export const DEFAULT_HUB_PERFORMANCE_MODE: HubPerformanceMode = 'balanced'

export const HUB_PERFORMANCE_QUERY_KEY = 'hubPerf'
export const HUB_FOCUS_QUERY_KEY = 'hubFocus'

export const HUB_PERFORMANCE_PROFILES: Record<
  HubPerformanceMode,
  HubPerformanceProfile
> = {
  diagnostic: {
    anisotropyLimit: 4,
    cameraDraggingSmoothTime: 0.08,
    cameraSmoothTime: 0.12,
    contactShadow: null,
    dpr: 1,
    enableButtonPulse: false,
    environment: false,
    environmentBackground: false,
    environmentBackgroundBlurriness: 0,
    environmentBackgroundIntensity: 0,
    environmentBackgroundRotation: [0, 0, 0],
    environmentIntensity: 0,
    environmentPreset: 'apartment',
    environmentRotation: [0, 0, 0],
    postProcessing: {
      ambientOcclusion: null,
      bloom: null,
      colorGrade: null,
      enabled: false,
      multisampling: 0,
      resolutionScale: 1,
    },
    shadowMapSize: 512,
    shadows: false,
    smoothCameraTransitions: false,
    toneMappingExposure: 0.96,
  },
  balanced: {
    anisotropyLimit: 8,
    cameraDraggingSmoothTime: 0.12,
    cameraSmoothTime: 0.38,
    contactShadow: {
      blur: 2.2,
      color: '#9f8cb9',
      far: 6.8,
      frames: Number.POSITIVE_INFINITY,
      opacity: 0.26,
      resolution: 1024,
      scale: 10.6,
    },
    dpr: [1, 1.25],
    enableButtonPulse: false,
    environment: true,
    environmentBackground: false,
    environmentBackgroundBlurriness: 0,
    environmentBackgroundIntensity: 0,
    environmentBackgroundRotation: [0, 0, 0],
    environmentIntensity: 0.2,
    environmentPreset: 'apartment',
    environmentRotation: [0, 0, 0],
    postProcessing: {
      ambientOcclusion: {
        aoRadius: 0.9,
        color: '#8d78aa',
        distanceFalloff: 0.72,
        halfRes: true,
        intensity: 0.42,
        quality: 'performance',
      },
      bloom: {
        intensity: 0.13,
        luminanceSmoothing: 0.72,
        luminanceThreshold: 0.84,
        mipmapBlur: true,
        radius: 0.54,
      },
      colorGrade: {
        brightness: -0.01,
        contrast: 0.018,
        saturation: -0.05,
      },
      enabled: true,
      multisampling: 0,
      resolutionScale: 0.82,
    },
    shadowMapSize: 1024,
    shadows: false,
    smoothCameraTransitions: true,
    toneMappingExposure: 0.82,
  },
  quality: {
    anisotropyLimit: null,
    cameraDraggingSmoothTime: 0.18,
    cameraSmoothTime: 0.72,
    contactShadow: {
      blur: 2.6,
      color: '#917dad',
      far: 7.2,
      frames: Number.POSITIVE_INFINITY,
      opacity: 0.32,
      resolution: 768,
      scale: 11,
    },
    dpr: [1, 1.25],
    enableButtonPulse: true,
    environment: true,
    environmentBackground: false,
    environmentBackgroundBlurriness: 0,
    environmentBackgroundIntensity: 0,
    environmentBackgroundRotation: [0, 0, 0],
    environmentIntensity: 0.18,
    environmentPreset: 'apartment',
    environmentRotation: [0, 0, 0],
    postProcessing: {
      ambientOcclusion: {
        aoRadius: 1.1,
        color: '#7f6a9d',
        distanceFalloff: 0.76,
        halfRes: true,
        intensity: 0.5,
        quality: 'performance',
      },
      bloom: {
        intensity: 0.04,
        luminanceSmoothing: 0.7,
        luminanceThreshold: 0.93,
        mipmapBlur: true,
        radius: 0.5,
      },
      colorGrade: {
        brightness: -0.025,
        contrast: 0.02,
        saturation: -0.04,
      },
      enabled: true,
      multisampling: 0,
      resolutionScale: 0.86,
    },
    shadowMapSize: 1024,
    shadows: false,
    smoothCameraTransitions: true,
    toneMappingExposure: 0.74,
  },
}

export const HUB_MONITOR_SCREEN_POSITION: [number, number, number] = [
  -2.4,
  3.25,
  -4.08,
]

export const HUB_MONITOR_SCREEN_SIZE: [number, number] = [2.58, 1.4]

export const HUB_PRINTER_POSITION: [number, number, number] = [-4.38, 2.56, -1.74]
export const HUB_PRINTER_ROTATION: [number, number, number] = [0, Math.PI * 0.5, 0]
export const HUB_PRINTER_SCALE = 0.42

export const HUB_NOTE_OUTPUT_POSITION: [number, number, number] = [
  -3.86,
  2.66,
  -2.9,
]

export const HUB_NOTE_OUTPUT_ROTATION: [number, number, number] = [
  0.32,
  -0.65,
  0.18,
]

export const HUB_WORKSPACE_SURFACE = {
  maximumX: -3.04,
  maximumZ: 0.18,
  minimumX: -5.82,
  minimumZ: -3.24,
  snapSize: 0.12,
  y: 2.74,
  zOffset: 0.018,
}

export const HUB_PEGBOARD_SURFACE = {
  maximumX: -3.62,
  maximumY: 4.48,
  minimumX: -5.78,
  minimumY: 3.08,
  snapSize: 0.14,
  z: -1.02,
  zOffset: 0.032,
}

export const HUB_WORKSPACE_DROP_CENTER: [number, number, number] = [
  (HUB_WORKSPACE_SURFACE.minimumX + HUB_WORKSPACE_SURFACE.maximumX) / 2,
  HUB_WORKSPACE_SURFACE.y + 0.006,
  (HUB_WORKSPACE_SURFACE.minimumZ + HUB_WORKSPACE_SURFACE.maximumZ) / 2,
]

export const HUB_WORKSPACE_DROP_SIZE: [number, number] = [
  HUB_WORKSPACE_SURFACE.maximumX - HUB_WORKSPACE_SURFACE.minimumX,
  HUB_WORKSPACE_SURFACE.maximumZ - HUB_WORKSPACE_SURFACE.minimumZ,
]

export const HUB_PEGBOARD_DROP_CENTER: [number, number, number] = [
  (HUB_PEGBOARD_SURFACE.minimumX + HUB_PEGBOARD_SURFACE.maximumX) / 2,
  (HUB_PEGBOARD_SURFACE.minimumY + HUB_PEGBOARD_SURFACE.maximumY) / 2,
  HUB_PEGBOARD_SURFACE.z + HUB_PEGBOARD_SURFACE.zOffset,
]

export const HUB_PEGBOARD_DROP_SIZE: [number, number] = [
  HUB_PEGBOARD_SURFACE.maximumX - HUB_PEGBOARD_SURFACE.minimumX,
  HUB_PEGBOARD_SURFACE.maximumY - HUB_PEGBOARD_SURFACE.minimumY,
]

export function parseHubPerformanceMode(
  value: string | null | undefined,
): HubPerformanceMode {
  if (value === 'balanced' || value === 'quality' || value === 'diagnostic') {
    return value
  }

  return DEFAULT_HUB_PERFORMANCE_MODE
}

export function getHubPerformanceModeFromSearch(
  search: string,
): HubPerformanceMode {
  return parseHubPerformanceMode(
    new URLSearchParams(search).get(HUB_PERFORMANCE_QUERY_KEY),
  )
}

export function parseHubFocusKey(
  value: string | null | undefined,
): HubFocusKey | null {
  if (
    value === 'overview' ||
    value === 'mainDesk' ||
    value === 'monitor' ||
    value === 'workspace' ||
    value === 'printer' ||
    value === 'pegboard'
  ) {
    return value
  }

  return null
}

export function getHubFocusKeyFromSearch(search: string): HubFocusKey | null {
  return parseHubFocusKey(new URLSearchParams(search).get(HUB_FOCUS_QUERY_KEY))
}

export const HUB_ROOM_SCALE = 7

export const HUB_ROOM_POSITION: [number, number, number] = [-0.9, -0.1, 0.34]

export const HUB_CAMERA_PRESETS: Record<
  HubFocusKey,
  {
    position: [number, number, number]
    target: [number, number, number]
  }
> = {
  overview: {
    position: [2.45, 5.05, 4.75],
    target: [-2.55, 2.42, -2.82],
  },
  mainDesk: {
    position: [2.35, 4.25, 1.65],
    target: [-2.4, 2.78, -3.62],
  },
  monitor: {
    position: [-2.38, 3.42, -0.92],
    target: [-2.38, 3.23, -4.12],
  },
  workspace: {
    position: [0.75, 4.65, 3.35],
    target: [-4.75, 2.62, -1.95],
  },
  printer: {
    position: [-1.92, 3.42, 0.26],
    target: [-3.82, 2.4, -3.05],
  },
  pegboard: {
    position: [-2.95, 4.42, 1.36],
    target: [-4.78, 3.62, -1.03],
  },
}
