import type { DrawingLine } from './drawing'

export type FlipbookRoomPhase = 'booth' | 'lobby' | 'drawing' | 'result' | 'closed'

export type FlipbookConnectionStatus =
  | 'idle'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected'
  | 'rejected'

export interface FlipbookRoomParticipant {
  userUuid: string
  nickname: string
  avatar: string
  joinedOrder: number
  isHost: boolean
  connectionStatus: 'online' | 'unstable' | 'offline'
}

export interface FlipbookSessionSettings {
  timeLimitSeconds: 30 | 45 | 60
  roundCount: number
  minimumRoundCount: number
  frameCountPerFlipbook: number
}

export interface FlipbookFramePayload {
  frameId: string
  index: number
  flipbookId: string
  drawnByUserUuid: string
  drawnByNickname: string
  lines: DrawingLine[]
  isEmpty: boolean
}

export interface FlipbookDrawingAssignment {
  roundIndex: number
  frameId: string
  flipbookId: string
  drawingUserUuid: string
  onionSkinFrameId: string | null
  onionSkinLines: DrawingLine[]
  deadlineAt: string | null
}

export interface FlipbookResultPayload {
  flipbookId: string
  frames: FlipbookFramePayload[]
  gifUrl: string | null
}

export interface FlipbookSessionSnapshot {
  roomId: string | null
  roomCode: string
  topic: string
  phase: FlipbookRoomPhase
  participants: FlipbookRoomParticipant[]
  settings: FlipbookSessionSettings
  activeAssignment: FlipbookDrawingAssignment | null
  currentFrameLines: DrawingLine[]
  completedFrames: FlipbookFramePayload[]
  result: FlipbookResultPayload | null
  serverSyncedAt: string | null
}

export type FlipbookClientMessage =
  | {
      type: 'flipbook.room.create'
      requestId: string
      payload: {
        userUuid: string | null
        nickname: string
      }
    }
  | {
      type: 'flipbook.room.join'
      requestId: string
      payload: {
        roomId: string | null
        roomCode: string
        userUuid: string | null
        nickname: string
      }
    }
  | {
      type: 'flipbook.settings.update'
      requestId: string
      payload: FlipbookSessionSettings
    }
  | {
      type: 'flipbook.game.start'
      requestId: string
      payload: {
        roomId: string | null
        settings: FlipbookSessionSettings
      }
    }
  | {
      type: 'flipbook.frame.draft'
      requestId: string
      payload: {
        roomId: string | null
        assignment: FlipbookDrawingAssignment | null
        lines: DrawingLine[]
      }
    }
  | {
      type: 'flipbook.frame.submit'
      requestId: string
      payload: {
        roomId: string | null
        assignment: FlipbookDrawingAssignment | null
        lines: DrawingLine[]
        submittedAt: string
      }
    }
  | {
      type: 'flipbook.room.leave'
      requestId: string
      payload: {
        roomId: string | null
        userUuid: string | null
      }
    }

export type FlipbookServerMessage =
  | {
      type: 'flipbook.snapshot'
      payload: FlipbookSessionSnapshot
    }
  | {
      type: 'flipbook.assignment.changed'
      payload: FlipbookDrawingAssignment
    }
  | {
      type: 'flipbook.participants.changed'
      payload: FlipbookRoomParticipant[]
    }
  | {
      type: 'flipbook.result.completed'
      payload: FlipbookResultPayload
    }
  | {
      type: 'flipbook.error'
      payload: {
        code: string
        message: string
      }
    }
