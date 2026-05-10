import type { DrawingLine } from '@/shared/types'

export type FlipbookStep = 'booth' | 'lobby' | 'drawing' | 'result'

export interface FlipbookParticipant {
  id: string
  userUuid: string
  name: string
  avatar: string
  isHost?: boolean
}

export type FlipbookTimeLimitSeconds = 30 | 45 | 60

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
