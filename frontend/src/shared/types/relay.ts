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
  // FACE 라운드는 이전 파트가 없으므로 null. BODY/LEGS는 객체 형태로 내려온다.
  // (가이드 §16: "FACE는 이전 파트가 없으므로 hint가 null이다")
  hint: RelayRoomAssignmentHintResponse | null
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
  | 'PARTICIPANT_DISCONNECTED'
  | 'PARTICIPANT_LEFT'
  | 'PARTICIPANT_DROPPED'
  | 'SETTINGS_CHANGED'
  | 'GAME_STARTED'
  | 'PART_STARTED'
  | 'PART_SUBMITTED'
  | 'PART_AUTO_SUBMITTED'
  | 'PART_TIME_UP'
  | 'ALL_PARTS_COMPLETED'
  | 'RESULT_CREATED'
  | 'HOST_CHANGED'
  | 'ROOM_CLOSED'
  | 'PARTICIPANT_KICKED'
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
  changedParticipant: RelayRoomParticipantResponse
}

export interface RelayWsParticipantDisconnectedData {
  roomCode: string
  userUuid: string
  nickname: string
  disconnectedAt: string
}

// 임시 — 백엔드가 PARTICIPANT_DISCONNECTED에서 퇴장 유저를 제거하면 이 타입/핸들러 제거 예정
export interface RelayWsParticipantLeftData {
  roomCode: string
  leftUserUuid: string
  leftNickname: string
  participantCount: number
  leftAt: string
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

// 라운드 데드라인 도달 — 백엔드가 미제출자에게 직접 보내는 자동 제출 지시.
// 클라이언트는 본인이 pendingSubmissions에 포함되어 있으면 즉시 제출 API를
// 발사하고, 그렇지 않으면 오버레이만 띄운 채 PART_STARTED를 기다린다.
// 백엔드는 모든 in-flight 제출이 완료될 때까지 다음 PART_STARTED를 보내지
// 않으므로 deadline-based 클라이언트 폴링이 필요 없다.
export interface RelayWsPartTimeUpPendingSubmission {
  canvasIndex: number
  userUuid: string
  nickname: string
  connected: boolean
}

export interface RelayWsPartTimeUpData {
  roomCode: string
  part: RelayPart
  partDeadlineAt: string
  submitGraceDeadlineAt: string
  autoSubmitGraceMillis: number
  pendingCount: number
  pendingSubmissions: RelayWsPartTimeUpPendingSubmission[]
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

// 호스트가 다른 참여자를 강퇴 — 방 전체 브로드캐스트.
// 강퇴 대상자에게는 별도로 KICKED_FROM_ROOM 개인 큐 이벤트가 함께 전달된다.
export interface RelayWsParticipantKickedData {
  roomCode: string
  kickedUserUuid: string
  kickedNickname: string
  participantCount: number
  kickedAt: string
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
  | RelayWsEnvelope<'PARTICIPANT_DISCONNECTED', RelayWsParticipantDisconnectedData>
  | RelayWsEnvelope<'PARTICIPANT_LEFT', RelayWsParticipantLeftData>
  | RelayWsEnvelope<'PARTICIPANT_DROPPED', RelayWsParticipantDroppedData>
  | RelayWsEnvelope<'SETTINGS_CHANGED', RelayWsSettingsChangedData>
  | RelayWsEnvelope<'GAME_STARTED', RelayWsGameStartedData>
  | RelayWsEnvelope<'PART_STARTED', RelayWsPartStartedData>
  | RelayWsEnvelope<'PART_SUBMITTED', RelayWsPartSubmittedData>
  | RelayWsEnvelope<'PART_AUTO_SUBMITTED', RelayWsPartAutoSubmittedData>
  | RelayWsEnvelope<'PART_TIME_UP', RelayWsPartTimeUpData>
  | RelayWsEnvelope<'ALL_PARTS_COMPLETED', RelayWsAllPartsCompletedData>
  | RelayWsEnvelope<'RESULT_CREATED', RelayWsResultCreatedData>
  | RelayWsEnvelope<'HOST_CHANGED', RelayWsHostChangedData>
  | RelayWsEnvelope<'ROOM_CLOSED', RelayWsRoomClosedData>
  | RelayWsEnvelope<'PARTICIPANT_KICKED', RelayWsParticipantKickedData>
  | RelayWsEnvelope<'KICKED_FROM_ROOM', RelayWsKickedFromRoomData>
  | RelayWsEnvelope<'DUPLICATE_SESSION_CLOSED', RelayWsDuplicateSessionClosedData>
