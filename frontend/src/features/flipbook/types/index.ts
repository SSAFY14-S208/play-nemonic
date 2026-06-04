export type FlipbookStep = 'booth' | 'lobby' | 'drawing' | 'result'

export interface FlipbookParticipant {
  id: string
  userUuid: string
  name: string
  avatar: string
  isHost?: boolean
  isConnected?: boolean
}

export type FlipbookTimeLimitSeconds = number

export type FlipbookDrawingSubmissionState = 'drawing' | 'submitting' | 'waiting'

export interface FlipbookTimeUpSubmitRequest {
  roomCode: string
  round: number
  assignmentKey: string
  roundDeadlineAt: string
  occurredAt: string
}
