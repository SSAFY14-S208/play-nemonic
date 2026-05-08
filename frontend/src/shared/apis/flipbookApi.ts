import { api } from '@/shared/libs'
import type {
  ApiResponse,
  FlipbookAssignmentResponse,
  FlipbookFrameSubmitRequest,
  FlipbookFrameSubmitResponse,
  FlipbookResultResponse,
  FlipbookRoomCreateResponse,
  FlipbookRoomKickResponse,
  FlipbookRoomLeaveResponse,
  FlipbookRoomStateResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /flipbook/rooms — 플립북 방 생성
export const postFlipbookRoom = () =>
  apiUnwrap(api.post<ApiResponse<FlipbookRoomCreateResponse>>('flipbook/rooms'))

// POST /flipbook/rooms/{roomCode}/start — 플립북 게임 시작
export const postFlipbookRoomStart = (roomCode: string) =>
  apiUnwrap(api.post<ApiResponse<FlipbookRoomStateResponse>>(`flipbook/rooms/${roomCode}/start`))

// PATCH /flipbook/rooms/{roomCode}/settings — 플립북 방 설정 변경
export const patchFlipbookRoomSettings = (roomCode: string, timeLimitSeconds: number) =>
  apiUnwrap(
    api.patch<ApiResponse<FlipbookRoomStateResponse>>(`flipbook/rooms/${roomCode}/settings`, {
      timeLimitSeconds,
    }),
  )

// GET /flipbook/rooms/{roomCode} — 플립북 대기실 정보 조회
export const getFlipbookRoom = (roomCode: string) =>
  apiUnwrap(api.get<ApiResponse<FlipbookRoomStateResponse>>(`flipbook/rooms/${roomCode}`))

// GET /flipbook/rooms/{roomCode}/assignments/me — 현재 라운드 내 프레임 배정 조회
export const getFlipbookRoomAssignmentMe = (roomCode: string) =>
  apiUnwrap(
    api.get<ApiResponse<FlipbookAssignmentResponse>>(
      `flipbook/rooms/${roomCode}/assignments/me`,
    ),
  )

// POST /flipbook/rooms/{roomCode}/rounds/{round}/frames — 현재 프레임 제출
export const postFlipbookRoomRoundFrame = (
  roomCode: string,
  round: number,
  payload: FlipbookFrameSubmitRequest,
) =>
  apiUnwrap(
    api.post<ApiResponse<FlipbookFrameSubmitResponse>>(
      `flipbook/rooms/${roomCode}/rounds/${round}/frames`,
      payload,
    ),
  )

// GET /flipbook/rooms/{roomCode}/result — 플립북 결과 조회
export const getFlipbookRoomResult = (roomCode: string) =>
  apiUnwrap(
    api.get<ApiResponse<FlipbookResultResponse>>(`flipbook/rooms/${roomCode}/result`),
  )

// POST /flipbook/rooms/{roomCode}/kick — 플립북 방 참여자 강퇴
// targetUserUuid는 강퇴 대상의 UUID(다른 사용자)이므로 body에 그대로 둔다.
export const postFlipbookRoomKick = (roomCode: string, targetUserUuid: string) =>
  apiUnwrap(
    api.post<ApiResponse<FlipbookRoomKickResponse>>(`flipbook/rooms/${roomCode}/kick`, {
      targetUserUuid,
    }),
  )

// DELETE /flipbook/rooms/{roomCode}/participants/me — 플립북 방 자발적 퇴장
export const deleteFlipbookRoomParticipantMe = (roomCode: string) =>
  apiUnwrap(
    api.delete<ApiResponse<FlipbookRoomLeaveResponse>>(
      `flipbook/rooms/${roomCode}/participants/me`,
    ),
  )
