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

export interface RelayRoomResultPartResponse {
  part: RelayPart
  drawerUserUuid: string
  drawerNickname: string
}

export interface RelayRoomResultItemResponse {
  canvasIndex: number
  galleryId: string
  artifactId: string
  thumbnailUrl: string
  contentUrl: string
  createdAt: string
  parts: RelayRoomResultPartResponse[]
}

export interface RelayRoomResultsResponse {
  roomCode: string
  roomStatus: RelayRoomStatus
  ready: boolean
  resultCount: number
  results: RelayRoomResultItemResponse[]
}

// WebSocket envelope — 가이드 §23
//   /topic/relay/rooms/{roomCode}      : 방 전체 브로드캐스트
//   /user/queue/relay/rooms/{roomCode} : 개인 큐
export type RelayWsEventType =
  | 'PARTICIPANT_CONNECTED'
  | 'PARTICIPANT_DROPPED'
  | 'SETTINGS_CHANGED'
  | 'GAME_STARTED'
  | 'PART_STARTED'
  | 'PART_SUBMITTED'
  | 'PART_AUTO_SUBMITTED'
  | 'ALL_PARTS_COMPLETED'
  | 'RESULT_CREATED'
  | 'HOST_CHANGED'
  | 'ROOM_CLOSED'
  | 'KICKED_FROM_ROOM'
  | 'DUPLICATE_SESSION_CLOSED'

export interface RelayWsEnvelope<TType extends RelayWsEventType, TData> {
  type: TType
  roomCode: string
  data: TData
  occurredAt: string
}

// 토픽 이벤트 (방 전체 브로드캐스트)
export interface RelayWsParticipantConnectedData {
  roomCode: string
  status: RelayRoomStatus
  hostUserUuid: string
  timeLimitSeconds: number
  minParticipants: number
  maxParticipants: number
  participantCount: number
  currentPart: RelayPart | null
  assignmentCount: number
  partStartedAt: string | null
  partDeadlineAt: string | null
  gameStartedAt: string | null
  participants: RelayRoomParticipantResponse[]
}

export interface RelayWsParticipantDroppedData {
  roomCode: string
  userUuid: string
  nickname: string
  disconnectedAt: string
  droppedAt: string
}

export interface RelayWsSettingsChangedData {
  roomCode: string
  status: RelayRoomStatus
  timeLimitSeconds: number
  participantCount: number
  participants: RelayRoomParticipantResponse[]
}

export interface RelayWsGameStartedData {
  roomCode: string
  status: RelayRoomStatus
  currentPart: RelayPart
  assignmentCount: number
  partStartedAt: string
  partDeadlineAt: string
  gameStartedAt: string
  participants: RelayRoomParticipantResponse[]
}

export interface RelayWsPartStartedData {
  roomCode: string
  previousPart: RelayPart | null
  part: RelayPart
  partStartedAt: string
  partDeadlineAt: string
  timeLimitSeconds: number
}

export interface RelayWsPartSubmittedData {
  userUuid: string
  nickname: string
  canvasIndex: number
  part: RelayPart
  assignmentStatus: RelayAssignmentStatus
  submittedAt: string
  submittedCount: number
  totalCount: number
  currentPartCompleted: boolean
}

export interface RelayWsPartAutoSubmittedData {
  roomCode: string
  userUuid: string
  nickname: string
  canvasIndex: number
  part: RelayPart
  assignmentStatus: RelayAssignmentStatus
  empty: boolean
  submittedAt: string
}

export interface RelayWsAllPartsCompletedData {
  roomCode: string
  roomStatus: RelayRoomStatus
  completedAt: string
}

export interface RelayWsResultCreatedItem {
  canvasIndex: number
  artifactId: string
  thumbnailUrl: string
  contentUrl: string
}

export interface RelayWsResultCreatedData {
  roomCode: string
  roomStatus: RelayRoomStatus
  artifactIds: string[]
  resultCount: number
  createdAt: string
  results: RelayWsResultCreatedItem[]
}

export interface RelayWsHostChangedData {
  roomCode: string
  previousHostUserUuid: string
  newHostUserUuid: string
  newHostNickname: string
  changedAt: string
}

export interface RelayWsRoomClosedData {
  roomCode: string
  roomStatus: RelayRoomStatus
  closedAt: string
}

// 개인 큐 이벤트 (해당 사용자에게만 전달)
export interface RelayWsKickedFromRoomData {
  message: string
}

export interface RelayWsDuplicateSessionClosedData {
  message: string
}

// Discriminated union — switch (evt.type)으로 narrowing 가능
export type RelayWsEvent =
  | RelayWsEnvelope<'PARTICIPANT_CONNECTED', RelayWsParticipantConnectedData>
  | RelayWsEnvelope<'PARTICIPANT_DROPPED', RelayWsParticipantDroppedData>
  | RelayWsEnvelope<'SETTINGS_CHANGED', RelayWsSettingsChangedData>
  | RelayWsEnvelope<'GAME_STARTED', RelayWsGameStartedData>
  | RelayWsEnvelope<'PART_STARTED', RelayWsPartStartedData>
  | RelayWsEnvelope<'PART_SUBMITTED', RelayWsPartSubmittedData>
  | RelayWsEnvelope<'PART_AUTO_SUBMITTED', RelayWsPartAutoSubmittedData>
  | RelayWsEnvelope<'ALL_PARTS_COMPLETED', RelayWsAllPartsCompletedData>
  | RelayWsEnvelope<'RESULT_CREATED', RelayWsResultCreatedData>
  | RelayWsEnvelope<'HOST_CHANGED', RelayWsHostChangedData>
  | RelayWsEnvelope<'ROOM_CLOSED', RelayWsRoomClosedData>
  | RelayWsEnvelope<'KICKED_FROM_ROOM', RelayWsKickedFromRoomData>
  | RelayWsEnvelope<'DUPLICATE_SESSION_CLOSED', RelayWsDuplicateSessionClosedData>
