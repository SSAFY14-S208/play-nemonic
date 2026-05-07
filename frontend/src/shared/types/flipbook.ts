import type { DrawingLine } from './drawing'

// REST API 도메인 (OpenAPI: tag "Flipbook")

export type FlipbookRoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED' | 'CLOSED'

export type FlipbookBlockedReason =
  | 'ROOM_FULL'
  | 'GAME_IN_PROGRESS'
  | 'KICKED'
  | 'ROOM_FINISHED'
  | 'ROOM_CLOSED'

export interface FlipbookRoomParticipantResponse {
  userUuid: string
  nickname: string
  host: boolean
  joinOrder: number
  connected: boolean
}

export interface FlipbookRoomViewerResponse {
  userUuid: string
  participant: boolean
  host: boolean
  canJoin: boolean
  canStart: boolean
  blockedReason: FlipbookBlockedReason | null
}

export interface FlipbookRoomCreateResponse {
  roomCode: string
  status: FlipbookRoomStatus
  hostUserUuid: string
  timeLimitSeconds: number
  minParticipants: number
  maxParticipants: number
  participantCount: number
  participants: FlipbookRoomParticipantResponse[]
  createdAt: string
}

export interface FlipbookRoomSettingsRequest {
  timeLimitSeconds: number
}

export interface FlipbookRoomKickRequest {
  targetUserUuid: string
}

export interface FlipbookRoomKickResponse {
  roomCode: string
  kickedUserUuid: string
  kickedNickname: string
  participantCount: number
  kickedAt: string
}

export interface FlipbookRoomLeaveResponse {
  roomCode: string
  leftUserUuid: string
  leftNickname: string
  participantCount: number
  hostChanged: boolean
  newHostUserUuid: string | null
  newHostNickname: string | null
  roomClosed: boolean
  roomStatus: FlipbookRoomStatus
  leftAt: string
}

export interface FlipbookRoomStateResponse {
  roomCode: string
  status: FlipbookRoomStatus
  hostUserUuid: string
  timeLimitSeconds: number
  minParticipants: number
  maxParticipants: number
  participantCount: number
  currentRound: number | null
  totalRounds: number | null
  roundStartedAt: string | null
  roundDeadlineAt: string | null
  gameStartedAt: string | null
  participants: FlipbookRoomParticipantResponse[]
  viewer: FlipbookRoomViewerResponse
  createdAt: string
  updatedAt: string
}

// WebSocket 세션 도메인 (REST와 별개의 실시간 페이로드 타입)

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
