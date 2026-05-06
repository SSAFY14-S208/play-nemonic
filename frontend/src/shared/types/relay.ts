// Relay 도메인 (OpenAPI: tag "Relay")

export type RelayRoomStatus = 'WAITING' | 'PLAYING' | 'FINALIZING' | 'FINISHED' | 'CLOSED'

export type RelayPart = 'FACE' | 'BODY' | 'LEGS'

export type RelayAssignmentStatus = 'PENDING' | 'SUBMITTED' | 'AUTO_SUBMITTED'

export type RelayBlockedReason =
  | 'ROOM_FULL'
  | 'GAME_IN_PROGRESS'
  | 'RECONNECT_EXPIRED'
  | 'KICKED'
  | 'ROOM_FINISHED'
  | 'ROOM_CLOSED'

export interface RelayRoomParticipantResponse {
  userUuid: string
  nickname: string
  host: boolean
  joinOrder: number
  connected: boolean
}

export interface RelayRoomViewerResponse {
  userUuid: string
  participant: boolean
  host: boolean
  canJoin: boolean
  canReconnect: boolean
  blockedReason: RelayBlockedReason
}

export interface RelayRoomCreateResponse {
  roomCode: string
  status: RelayRoomStatus
  hostUserUuid: string
  timeLimitSeconds: number
  minParticipants: number
  maxParticipants: number
  participantCount: number
  participants: RelayRoomParticipantResponse[]
  createdAt: string
}

export interface RelayRoomStateResponse {
  roomCode: string
  status: RelayRoomStatus
  hostUserUuid: string
  timeLimitSeconds: number
  minParticipants: number
  maxParticipants: number
  participantCount: number
  currentPart: RelayPart
  assignmentCount: number
  partStartedAt: string
  partDeadlineAt: string
  gameStartedAt: string
  participants: RelayRoomParticipantResponse[]
  viewer: RelayRoomViewerResponse
  createdAt: string
  updatedAt: string
}

export interface RelayRoomSettingsRequest {
  timeLimitSeconds: number
}

export interface RelayRoomKickRequest {
  targetUserUuid: string
}

export interface RelayRoomKickResponse {
  roomCode: string
  kickedUserUuid: string
  kickedNickname: string
  participantCount: number
  kickedAt: string
}

export interface RelayRoomCloseResponse {
  roomCode: string
  roomStatus: RelayRoomStatus
  closedAt: string
  alreadyClosed: boolean
}

export interface RelayRoomLeaveResponse {
  roomCode: string
  leftUserUuid: string
  leftNickname: string
  participantCount: number
  hostChanged: boolean
  newHostUserUuid: string
  newHostNickname: string
  roomClosed: boolean
  roomStatus: RelayRoomStatus
  leftAt: string
}

export interface RelayRoomSubmissionResponse {
  roomCode: string
  canvasIndex: number
  part: RelayPart
  assignmentStatus: RelayAssignmentStatus
  drawingObjectKey: string
  hintObjectKey: string | null
  submittedAt: string
  alreadySubmitted: boolean
  currentPartCompleted: boolean
  submittedCount: number
  totalCount: number
  advanced: boolean
  nextPart: RelayPart | null
  nextPartStartedAt: string
  nextPartDeadlineAt: string
  allPartsCompleted: boolean
  roomStatus: RelayRoomStatus
}

export interface RelayRoomAssignmentHintResponse {
  previousPart: RelayPart
  canvasIndex: number
  objectKey: string
  url: string | null
  empty: boolean
}

export interface RelayRoomMyAssignmentResponse {
  roomCode: string
  canvasIndex: number
  part: RelayPart
  assignmentStatus: RelayAssignmentStatus
  timeLimitSeconds: number
  partStartedAt: string
  partDeadlineAt: string
  remainingSeconds: number
  hint: RelayRoomAssignmentHintResponse
}
