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
