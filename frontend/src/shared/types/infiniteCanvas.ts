// Infinite Canvas domain (backend: /api/v1/infinite-canvas/canvases)

export type InfiniteCanvasStatus = 'ACTIVE' | 'CLOSED'

export type InfiniteCanvasOperationType =
  | 'CREATE_ELEMENT'
  | 'UPDATE_ELEMENT'
  | 'UPSERT_ELEMENT'
  | 'DELETE_ELEMENT'
  | 'CLEAR_CANVAS'

export type InfiniteCanvasConnectionStatus =
  | 'idle'
  | 'connecting'
  | 'connected'
  | 'reconnecting'
  | 'disconnected'
  | 'rejected'

export type InfiniteCanvasJsonObject = Record<string, unknown>

export interface InfiniteCanvasParticipantResponse {
  userUuid: string
  nickname: string
  color: string
  avatarUrl: string | null
  host: boolean
  connected: boolean
  joinedAt: string
  lastConnectedAt: string | null
}

export interface InfiniteCanvasLock {
  elementId: string
  userUuid: string
  lockedAt: string
  expiresAt: string | null
}

export interface InfiniteCanvasCursor {
  userUuid: string
  x: number | null
  y: number | null
  zoom: number | null
  payload: InfiniteCanvasJsonObject | null
  updatedAt: string
}

export interface InfiniteCanvasOperation {
  operationId: string
  clientOperationId: string
  operationType: InfiniteCanvasOperationType
  elementId: string | null
  element: InfiniteCanvasJsonObject | null
  payload: InfiniteCanvasJsonObject | null
  userUuid: string
  revision: number
  occurredAt: string
}

export interface InfiniteCanvasStateResponse {
  roomCode: string
  status: InfiniteCanvasStatus
  hostUserUuid: string
  me: InfiniteCanvasParticipantResponse | null
  participants: InfiniteCanvasParticipantResponse[]
  elements: InfiniteCanvasJsonObject[]
  operations: InfiniteCanvasOperation[]
  locks: Record<string, InfiniteCanvasLock>
  viewport: InfiniteCanvasJsonObject | null
  maxParticipants: number
  revision: number
  createdAt: string
  updatedAt: string
}

export interface InfiniteCanvasParticipantEventResponse {
  roomCode: string
  status: InfiniteCanvasStatus
  hostUserUuid: string
  participants: InfiniteCanvasParticipantResponse[]
  changedParticipant: InfiniteCanvasParticipantResponse | null
  maxParticipants: number
  revision: number
  updatedAt: string
}

export interface InfiniteCanvasCreateRequest {
  color?: string | null
}

export interface InfiniteCanvasParticipantColorUpdateRequest {
  color: string
}

export interface InfiniteCanvasCreateResponse {
  roomCode: string
  status: InfiniteCanvasStatus
  hostUserUuid: string
  maxParticipants: number
  participantCount: number
  participants: InfiniteCanvasParticipantResponse[]
  createdAt: string
}

export interface InfiniteCanvasOperationRequest {
  operationId?: string | null
  clientOperationId: string
  operationType: InfiniteCanvasOperationType
  elementId?: string | null
  element?: InfiniteCanvasJsonObject | null
  payload?: InfiniteCanvasJsonObject | null
}

export interface InfiniteCanvasOpsRequest {
  baseRevision: number
  operations: InfiniteCanvasOperationRequest[]
}

export interface InfiniteCanvasSnapshotRequest {
  baseRevision: number
  elements: InfiniteCanvasJsonObject[]
  viewport?: InfiniteCanvasJsonObject | null
}

export interface InfiniteCanvasCursorRequest {
  x: number
  y: number
  zoom?: number | null
  payload?: InfiniteCanvasJsonObject | null
}

export interface InfiniteCanvasLockRequest {
  elementId: string
}

export interface InfiniteCanvasLeaveResponse {
  roomCode: string
  userUuid: string
  nickname: string | null
  participantCount: number
  hostChanged: boolean
  newHostUserUuid: string | null
  newHostNickname: string | null
  closed: boolean
  closedAt: string | null
}

export interface InfiniteCanvasOpsAppliedResponse {
  roomCode: string
  revision: number
  elementCount: number
  operations: InfiniteCanvasOperation[]
}

export interface InfiniteCanvasRevisionConflictResponse {
  roomCode: string
  baseRevision: number
  latestRevision: number
  missingOperations: InfiniteCanvasOperation[]
  fullStateRequired: boolean
}

export interface InfiniteCanvasLockResponse {
  roomCode: string
  elementId: string
  lock: InfiniteCanvasLock | null
}

export interface InfiniteCanvasCursorResponse {
  roomCode: string
  cursor: InfiniteCanvasCursor
}

export interface InfiniteCanvasOutputSaveRequest {
  imageFileId: string
  thumbnailFileId?: string | null
  meta?: InfiniteCanvasJsonObject | null
}

export interface InfiniteCanvasOutputSaveResponse {
  galleryId: string
  artifactId: string
  kind: 'infinite_canvas' | string
  roomCode: string
  thumbnailUrl: string
  contentUrl: string
  createdAt: string
}

export interface InfiniteCanvasAiStickerCreateRequest {
  prompt: string
  style?: string | null
  width?: number | null
  height?: number | null
  transparentBackground?: boolean | null
}

export interface InfiniteCanvasAiStickerCreateResponse {
  stickerId: string
  imageUrl: string
  objectKey: string
  contentType: string
  width: number
  height: number
  element: InfiniteCanvasJsonObject
}

export type InfiniteCanvasWsEventType =
  | 'STATE_SNAPSHOT'
  | 'SNAPSHOT_UPDATED'
  | 'OPS_APPLIED'
  | 'CURSOR_UPDATED'
  | 'LOCK_ACQUIRED'
  | 'LOCK_RELEASED'
  | 'PARTICIPANT_CONNECTED'
  | 'PARTICIPANT_DISCONNECTED'
  | 'PARTICIPANT_LEFT'
  | 'HOST_CHANGED'
  | 'PARTICIPANT_UPDATED'
  | 'CANVAS_CLOSED'
  | 'DUPLICATE_SESSION_CLOSED'
  | 'PONG'
  | 'ERROR'

export interface InfiniteCanvasSimpleMessageResponse {
  message: string
  details?: InfiniteCanvasRevisionConflictResponse | null
}

export interface InfiniteCanvasWsEventPayloadMap {
  STATE_SNAPSHOT: InfiniteCanvasStateResponse
  SNAPSHOT_UPDATED: InfiniteCanvasStateResponse
  OPS_APPLIED: InfiniteCanvasOpsAppliedResponse
  CURSOR_UPDATED: InfiniteCanvasCursorResponse
  LOCK_ACQUIRED: InfiniteCanvasLockResponse
  LOCK_RELEASED: InfiniteCanvasLockResponse
  PARTICIPANT_CONNECTED: InfiniteCanvasParticipantEventResponse
  PARTICIPANT_DISCONNECTED: InfiniteCanvasParticipantEventResponse
  PARTICIPANT_LEFT: InfiniteCanvasLeaveResponse
  HOST_CHANGED: InfiniteCanvasLeaveResponse
  PARTICIPANT_UPDATED: InfiniteCanvasParticipantResponse
  CANVAS_CLOSED: InfiniteCanvasSimpleMessageResponse | string
  DUPLICATE_SESSION_CLOSED: InfiniteCanvasSimpleMessageResponse
  PONG: InfiniteCanvasSimpleMessageResponse
  ERROR: InfiniteCanvasSimpleMessageResponse
}

export interface InfiniteCanvasWsEnvelope<TType extends InfiniteCanvasWsEventType> {
  type: TType
  roomCode: string
  data: InfiniteCanvasWsEventPayloadMap[TType]
  occurredAt: string
}

export type InfiniteCanvasWsEvent = {
  [TType in InfiniteCanvasWsEventType]: InfiniteCanvasWsEnvelope<TType>
}[InfiniteCanvasWsEventType]

export type InfiniteCanvasRealtimeEventType = InfiniteCanvasWsEventType

export type InfiniteCanvasRealtimeEvent = InfiniteCanvasWsEvent
