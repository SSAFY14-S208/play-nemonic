export type HubFocusKey =
  | 'overview'
  | 'mainDesk'
  | 'monitor'
  | 'workspace'
  | 'printer'
  | 'pegboard'
  | 'communityBoard'

export type HubPerformanceMode = 'diagnostic' | 'balanced' | 'quality'

export interface HubBloomProfile {
  intensity: number
  luminanceSmoothing: number
  luminanceThreshold: number
  mipmapBlur: boolean
  radius: number
}

export interface HubAmbientOcclusionProfile {
  aoRadius: number
  color: string
  distanceFalloff: number
  halfRes: boolean
  intensity: number
  quality: 'performance' | 'low' | 'medium' | 'high' | 'ultra'
}

export interface HubColorGradeProfile {
  brightness: number
  contrast: number
  saturation: number
}

export interface HubContactShadowProfile {
  blur: number
  color: string
  far: number
  frames: number
  opacity: number
  resolution: number
  scale: number
}

export interface HubPostProcessingProfile {
  ambientOcclusion: HubAmbientOcclusionProfile | null
  bloom: HubBloomProfile | null
  colorGrade: HubColorGradeProfile | null
  enabled: boolean
  multisampling: number
  resolutionScale: number
}

export interface HubPerformanceProfile {
  anisotropyLimit: number | null
  cameraDraggingSmoothTime: number
  cameraSmoothTime: number
  contactShadow: HubContactShadowProfile | null
  dpr: number | [number, number]
  enableButtonPulse: boolean
  environment: boolean
  environmentBackground: boolean
  environmentBackgroundBlurriness: number
  environmentBackgroundIntensity: number
  environmentBackgroundRotation: [number, number, number]
  environmentIntensity: number
  environmentPreset: 'apartment' | 'forest'
  environmentRotation: [number, number, number]
  postProcessing: HubPostProcessingProfile
  shadowMapSize: number
  shadows: boolean
  smoothCameraTransitions: boolean
  toneMappingExposure: number
}

export type NoteSurface = 'floating' | 'workspace' | 'pegboard'

export interface PrintedNote {
  createdAt: number
  id: string
  imageDataUrl: string | null
  position: [number, number, number]
  rotation: [number, number, number]
  surface: NoteSurface
  text: string
}

export interface PrintRequest {
  id: string
  imageDataUrl: string | null
  requestedAt: number
  text: string
}

export type PrintStatus = 'idle' | 'requested' | 'printing' | 'complete'
