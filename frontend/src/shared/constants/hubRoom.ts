import type {
  HubFocusKey,
  HubPerformanceMode,
  HubPerformanceProfile,
} from '@/shared/types'

export const HUB_ROOM_MODEL_PATH = '/models/isometric-girl-room.glb?v=deci-nemonic-printer-webp2048-20260514-2308'

export const DEFAULT_HUB_PERFORMANCE_MODE: HubPerformanceMode = 'diagnostic'

export const HUB_PERFORMANCE_QUERY_KEY = 'hubPerf'

export const HUB_PERFORMANCE_PROFILES: Record<
  HubPerformanceMode,
  HubPerformanceProfile
> = {
  diagnostic: {
    anisotropyLimit: 4,
    cameraDraggingSmoothTime: 0.08,
    cameraSmoothTime: 0.12,
    contactShadows: false,
    dpr: 1,
    enableButtonPulse: false,
    environment: false,
    shadows: false,
    smoothCameraTransitions: false,
  },
  balanced: {
    anisotropyLimit: 8,
    cameraDraggingSmoothTime: 0.12,
    cameraSmoothTime: 0.38,
    contactShadows: false,
    dpr: [1, 1.25],
    enableButtonPulse: false,
    environment: true,
    shadows: false,
    smoothCameraTransitions: true,
  },
  quality: {
    anisotropyLimit: null,
    cameraDraggingSmoothTime: 0.18,
    cameraSmoothTime: 0.72,
    contactShadows: true,
    dpr: [1, 1.5],
    enableButtonPulse: true,
    environment: true,
    shadows: true,
    smoothCameraTransitions: true,
  },
}

export const HUB_MONITOR_SCREEN_POSITION: [number, number, number] = [
  -2.4,
  3.25,
  -4.08,
]

export const HUB_MONITOR_SCREEN_SIZE: [number, number] = [2.58, 1.4]

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
