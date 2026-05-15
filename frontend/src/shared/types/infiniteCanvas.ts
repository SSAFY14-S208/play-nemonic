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
  canvasId: string
  inviteCode: string
  status: InfiniteCanvasStatus
  ownerUserUuid: string
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

export interface InfiniteCanvasCreateRequest {
  nickname?: string | null
  color?: string | null
  avatarUrl?: string | null
  viewport?: InfiniteCanvasJsonObject | null
}

export interface InfiniteCanvasParticipantUpdateRequest {
  nickname?: string | null
  color?: string | null
  avatarUrl?: string | null
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
  canvasId: string
  userUuid: string
  closed: boolean
  closedAt: string | null
}

export interface InfiniteCanvasOpsAppliedResponse {
  canvasId: string
  revision: number
  elementCount: number
  operations: InfiniteCanvasOperation[]
}

export interface InfiniteCanvasLockResponse {
  canvasId: string
  elementId: string
  lock: InfiniteCanvasLock | null
}

export interface InfiniteCanvasCursorResponse {
  canvasId: string
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
  canvasId: string
  thumbnailUrl: string
  contentUrl: string
  createdAt: string
}
