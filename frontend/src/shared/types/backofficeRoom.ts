// Backoffice Relay/Flipbook/Infinite Canvas Rooms 도메인
// (OpenAPI: tags "Backoffice Relay Rooms", "Backoffice Flipbook Rooms", "Backoffice Infinite Canvas")

import type { InfiniteCanvasStatus } from './infiniteCanvas'
import type { RelayRoomStatus } from './relay'

/**
 * 백오피스 활성 릴레이 방 응답.
 *
 * Swagger(2026-05-12) 기준 — host 정보·createdAt·finishedAt 등은 백엔드가 제공하지 않는다.
 * WAITING 상태에서는 gameStartedAt이 null.
 * 백엔드는 "종료되지 않은" 방만 내려주므로 status는 실제로는 WAITING/PLAYING/FINALIZING/FINISHED
 * 중 하나지만, 타입은 user-facing `RelayRoomStatus`(CLOSED 포함 5값)를 그대로 재사용한다.
 */
export interface BackofficeRelayRoomResponse {
  roomCode: string
  status: RelayRoomStatus
  participantCount: number
  gameStartedAt: string | null
}

export interface BackofficeRelayRoomListResponse {
  items: BackofficeRelayRoomResponse[]
  totalElements: number
  page: number
  size: number
}

/**
 * 백오피스 릴레이 방 삭제(강제 CLOSED 전환) 응답.
 * Swagger 응답 데이터에는 roomCode만 포함된다.
 */
export interface BackofficeRelayRoomDeleteResponse {
  roomCode: string
}

/**
 * 백오피스 플립북 방 상태.
 *
 * 사용자 흐름의 `FlipbookRoomStatus`(WAITING/PLAYING/FINISHED/CLOSED)와 별도로,
 * 백오피스 응답 enum 스키마에는 `FINALIZING`이 포함되어 있어 별도 타입으로 둔다.
 * 단, 백엔드 응답에서 CLOSED는 제외돼 내려오지 않는다(요청 옵션과 무관).
 */
export type BackofficeFlipbookRoomStatus =
  | 'WAITING'
  | 'PLAYING'
  | 'FINALIZING'
  | 'FINISHED'
  | 'CLOSED'

/**
 * 백오피스 활성 플립북 방 응답.
 *
 * Swagger(2026-05-12) 기준 — host 정보·createdAt·finishedAt 등은 백엔드가 제공하지 않는다.
 * WAITING 상태에서는 currentRound / totalRounds / gameStartedAt 모두 null.
 */
export interface BackofficeFlipbookRoomResponse {
  roomCode: string
  status: BackofficeFlipbookRoomStatus
  participantCount: number
  currentRound: number | null
  totalRounds: number | null
  gameStartedAt: string | null
}

export interface BackofficeFlipbookRoomListResponse {
  items: BackofficeFlipbookRoomResponse[]
  totalElements: number
  page: number
  size: number
}

/**
 * 백오피스 플립북 방 삭제(강제 CLOSED 전환) 응답.
 * Swagger 응답 데이터에는 roomCode만 포함된다.
 */
export interface BackofficeFlipbookRoomDeleteResponse {
  roomCode: string
}

/**
 * 백오피스 활성 무한 캔버스 응답.
 *
 * 무한 캔버스는 내부 드로잉/요소를 백오피스에서 열람하지 않고,
 * 활성 캔버스 메타데이터만 조회한다.
 */
export interface BackofficeInfiniteCanvasResponse {
  roomCode: string
  status: InfiniteCanvasStatus
  hostUserUuid: string
  participantCount: number
  maxParticipants: number
  connectedParticipantCount: number
  elementCount: number
  revision: number
  createdAt: string
  updatedAt: string
}

export interface BackofficeInfiniteCanvasListResponse {
  items: BackofficeInfiniteCanvasResponse[]
  totalElements: number
  page: number
  size: number
}

/**
 * 백오피스 무한 캔버스 삭제(강제 종료) 응답.
 * Swagger 응답 데이터에는 roomCode만 포함된다.
 */
export interface BackofficeInfiniteCanvasCloseResponse {
  roomCode: string
}

/**
 * GET /backoffice/infinite-canvas/canvases 쿼리 파라미터.
 *
 * status는 ACTIVE만 허용된다. 생략 시 백엔드 기본값도 ACTIVE.
 */
export interface BackofficeInfiniteCanvasListParams {
  status?: 'ACTIVE'
  page?: number
  size?: number
}

/**
 * GET /backoffice/{relay|flipbook}-rooms 쿼리 파라미터.
 *
 * - relay 필터 status: 'WAITING' | 'PLAYING' | 'FINALIZING' | 'FINISHED' (CLOSED 불가)
 * - flipbook 필터 status: 'WAITING' | 'PLAYING' | 'FINISHED' (CLOSED·FINALIZING 불가)
 *
 * 호출 측에서 적절한 값을 넣어야 하므로 타입은 `string`으로 유지하고 사용 위치에서 좁힌다.
 * `size`는 백엔드가 1~100 범위로 클램프 (기본 20).
 */
export interface BackofficeRoomListParams {
  status?: string
  page?: number
  size?: number
}
