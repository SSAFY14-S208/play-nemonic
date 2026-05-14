export type HubFocusKey =
  | 'overview'
  | 'mainDesk'
  | 'monitor'
  | 'workspace'
  | 'printer'
  | 'pegboard'

export type HubPerformanceMode = 'diagnostic' | 'balanced' | 'quality'

export interface HubPerformanceProfile {
  anisotropyLimit: number | null
  cameraDraggingSmoothTime: number
  cameraSmoothTime: number
  contactShadows: boolean
  dpr: number | [number, number]
  enableButtonPulse: boolean
  environment: boolean
  shadows: boolean
  smoothCameraTransitions: boolean
}
