import type { DrawingLine } from '@/shared/types'

export interface FlipbookFrame {
  id: string
  index: number
  drawnByUserUuid: string
  drawnBy: string
  participantAvatar: string
  lines: DrawingLine[]
  imageUrl?: string
}

export type FlipbookDrawingSubmissionState = 'drawing' | 'submitting' | 'waiting'
