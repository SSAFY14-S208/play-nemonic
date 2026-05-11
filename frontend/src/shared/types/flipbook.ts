import type { DrawingLine } from './drawing'

// REST API 도메인 (OpenAPI: tag "Flipbook")

export type FlipbookRoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED' | 'CLOSED'

export type FlipbookBlockedReason =
  | 'ROOM_FULL'
  | 'GAME_IN_PROGRESS'
  | 'KICKED'
  | 'RECONNECT_EXPIRED'
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
  timeLimitSeconds?: number
  roundCount?: number
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

export type FlipbookAssignmentStatus = 'PENDING' | 'SUBMITTED' | 'AUTO_SUBMITTED'

export interface FlipbookAssignmentHintResponse {
  flipbookIndex: number
  frameIndex: number
  round: number
  imageUrl: string | null
  objectKey?: string | null
  url?: string | null
  empty: boolean
}

export interface FlipbookAssignmentResponse {
  roomCode: string
  currentRound: number
  totalRounds: number
  flipbookIndex: number
  frameIndex: number
  assignmentStatus: FlipbookAssignmentStatus
  timeLimitSeconds: number
  roundStartedAt: string
  roundDeadlineAt: string
  remainingSeconds: number
  hint: FlipbookAssignmentHintResponse | null
}

export interface FlipbookFrameSubmitRequest {
  flipbookIndex: number
  frameIndex: number
  fileId: string
}

export interface FlipbookFrameSubmitResponse {
  roomCode: string
  round: number
  flipbookIndex: number
  frameIndex: number
  assignmentStatus: FlipbookAssignmentStatus
  fileId: string
  objectKey: string
  frameUrl: string
  submittedAt: string
  alreadySubmitted: boolean
  currentRoundCompleted: boolean
  submittedCount: number
  totalCount: number
  advanced: boolean
  nextRound: number | null
  nextRoundStartedAt: string | null
  nextRoundDeadlineAt: string | null
  allRoundsCompleted: boolean
  roomStatus: FlipbookRoomStatus
}

export interface FlipbookResultFrameResponse {
  frameIndex: number
  imageUrl: string
  drawnByUserUuid: string
  drawnByNickname: string
}

export interface FlipbookResultItemResponse {
  flipbookIndex: number
  galleryId: string
  artifactId: string
  thumbnailUrl: string
  gifUrl: string
  firstImageUrl: string
  createdAt: string
  frames: FlipbookResultFrameResponse[]
}

export interface FlipbookResultResponse {
  roomCode: string
  roomStatus: FlipbookRoomStatus
  ready: boolean
  resultCount: number
  results: FlipbookResultItemResponse[]
}

export interface FlipbookRealtimeEvent<TData = unknown> {
  type: string
  roomCode: string
  data: TData
  occurredAt: string
}

export type FlipbookWsEventType =
  | 'PARTICIPANT_CONNECTED'
  | 'PARTICIPANT_DISCONNECTED'
  | 'PARTICIPANT_DROPPED'
  | 'SETTINGS_CHANGED'
  | 'GAME_STARTED'
  | 'ROUND_TIME_UP'
  | 'FRAME_SUBMITTED'
  | 'FRAME_AUTO_SUBMITTED'
  | 'ROUND_STARTED'
  | 'ALL_ROUNDS_COMPLETED'
  | 'ROOM_CLOSED'
  | 'PARTICIPANT_KICKED'
  | 'PARTICIPANT_LEFT'
  | 'HOST_CHANGED'
  | 'KICKED_FROM_ROOM'
  | 'DUPLICATE_SESSION_CLOSED'
  | 'PONG'
  | 'ERROR'

export interface FlipbookWsEnvelope<TType extends FlipbookWsEventType, TData> {
  type: TType
  roomCode: string
  data: TData
  occurredAt: string
}

export interface FlipbookRoomSnapshotResponse {
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
  changedParticipant: FlipbookRoomParticipantResponse | null
  createdAt: string
  updatedAt: string
}

export interface FlipbookWsParticipantDroppedData {
  roomCode: string
  userUuid: string
  nickname: string
  disconnectedAt: string
  droppedAt: string
}

export interface FlipbookWsFrameAutoSubmittedData {
  roomCode: string
  userUuid: string
  nickname: string
  flipbookIndex: number
  frameIndex: number
  round: number
  assignmentStatus: 'AUTO_SUBMITTED'
  empty: boolean
  submittedAt: string
}

export interface FlipbookWsRoundStartedData {
  roomCode: string
  previousRound: number
  round: number
  roundStartedAt: string
  roundDeadlineAt: string
  timeLimitSeconds: number
}

export interface FlipbookWsRoundTimeUpData {
  roomCode: string
  round: number
  roundDeadlineAt: string
  submitGraceDeadlineAt: string
  autoSubmitGraceMillis: number
}

export interface FlipbookWsAllRoundsCompletedData {
  roomCode: string
  roomStatus: 'FINISHED'
  completedAt: string
}

export interface FlipbookWsRoomClosedData {
  roomCode: string
  roomStatus: 'CLOSED'
  closedAt: string
}

export interface FlipbookWsParticipantKickedData {
  roomCode: string
  kickedUserUuid: string
  kickedNickname: string
  participantCount: number
  kickedAt: string
}

export interface FlipbookWsParticipantLeftData {
  roomCode: string
  leftUserUuid: string
  leftNickname: string
  participantCount: number
  leftAt: string
}

export interface FlipbookWsHostChangedData {
  roomCode: string
  previousHostUserUuid: string
  newHostUserUuid: string
  newHostNickname: string
  changedAt: string
}

export interface FlipbookWsMessageData {
  message: string
}

export type FlipbookWsEvent =
  | FlipbookWsEnvelope<'PARTICIPANT_CONNECTED', FlipbookRoomSnapshotResponse>
  | FlipbookWsEnvelope<'PARTICIPANT_DISCONNECTED', FlipbookRoomSnapshotResponse>
  | FlipbookWsEnvelope<'SETTINGS_CHANGED', FlipbookRoomSnapshotResponse>
  | FlipbookWsEnvelope<'GAME_STARTED', FlipbookRoomSnapshotResponse>
  | FlipbookWsEnvelope<'ROUND_TIME_UP', FlipbookWsRoundTimeUpData>
  | FlipbookWsEnvelope<'FRAME_SUBMITTED', FlipbookFrameSubmitResponse>
  | FlipbookWsEnvelope<'FRAME_AUTO_SUBMITTED', FlipbookWsFrameAutoSubmittedData>
  | FlipbookWsEnvelope<'ROUND_STARTED', FlipbookWsRoundStartedData>
  | FlipbookWsEnvelope<'ALL_ROUNDS_COMPLETED', FlipbookWsAllRoundsCompletedData>
  | FlipbookWsEnvelope<'ROOM_CLOSED', FlipbookWsRoomClosedData>
  | FlipbookWsEnvelope<'PARTICIPANT_KICKED', FlipbookWsParticipantKickedData>
  | FlipbookWsEnvelope<'PARTICIPANT_LEFT', FlipbookWsParticipantLeftData>
  | FlipbookWsEnvelope<'PARTICIPANT_DROPPED', FlipbookWsParticipantDroppedData>
  | FlipbookWsEnvelope<'HOST_CHANGED', FlipbookWsHostChangedData>
  | FlipbookWsEnvelope<'KICKED_FROM_ROOM', FlipbookWsMessageData>
  | FlipbookWsEnvelope<'DUPLICATE_SESSION_CLOSED', FlipbookWsMessageData>
  | FlipbookWsEnvelope<'PONG', FlipbookWsMessageData>
  | FlipbookWsEnvelope<'ERROR', FlipbookWsMessageData>

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
