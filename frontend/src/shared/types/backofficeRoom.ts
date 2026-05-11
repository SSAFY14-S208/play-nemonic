// Backoffice Relay/Flipbook Rooms 도메인
// (OpenAPI: tags "Backoffice Relay Rooms", "Backoffice Flipbook Rooms")

import type { FlipbookRoomStatus } from './flipbook'
import type { RelayRoomStatus } from './relay'

export interface BackofficeRelayRoomResponse {
  roomCode: string
  status: RelayRoomStatus
  hostUuid: string
  hostNickname: string
  participantCount: number
  maxParticipants: number
  timeLimit: number
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface BackofficeRelayRoomListResponse {
  rooms: BackofficeRelayRoomResponse[]
  totalCount: number
  pageNumber: number
  pageSize: number
}

export interface BackofficeRelayRoomDeleteResponse {
  roomCode: string
  previousStatus: RelayRoomStatus
  newStatus: 'CLOSED'
  closedAt: string
}

export interface BackofficeFlipbookRoomResponse {
  roomCode: string
  status: FlipbookRoomStatus
  hostUuid: string
  hostNickname: string
  participantCount: number
  maxParticipants: number
  timeLimit: number
  currentRound: number
  totalRounds: number
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface BackofficeFlipbookRoomListResponse {
  rooms: BackofficeFlipbookRoomResponse[]
  totalCount: number
  pageNumber: number
  pageSize: number
}

export interface BackofficeFlipbookRoomDeleteResponse {
  roomCode: string
  previousStatus: FlipbookRoomStatus
  newStatus: 'CLOSED'
  closedAt: string
}

export interface BackofficeRoomListParams {
  status?: string
  page?: number
  size?: number
}
