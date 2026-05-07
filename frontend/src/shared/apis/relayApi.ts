import { api } from '@/shared/libs'
import type {
  ApiResponse,
  RelayRoomCloseResponse,
  RelayRoomCreateResponse,
  RelayRoomKickResponse,
  RelayRoomLeaveResponse,
  RelayRoomMyAssignmentResponse,
  RelayRoomStateResponse,
  RelayRoomSubmissionResponse,
} from '@/shared/types'

import { apiUnwrap } from '@/shared/utils'

// POST /relay/rooms — 릴레이 방 생성
export const postRelayRoom = () =>
  apiUnwrap(api.post<ApiResponse<RelayRoomCreateResponse>>('relay/rooms'))

// POST /relay/rooms/{roomCode}/start — 릴레이 게임 시작
export const postRelayRoomStart = (roomCode: string) =>
  apiUnwrap(api.post<ApiResponse<RelayRoomStateResponse>>(`relay/rooms/${roomCode}/start`))

// POST /relay/rooms/{roomCode}/participants — 릴레이 방 입장/복귀
export const postRelayRoomParticipant = (roomCode: string) =>
  apiUnwrap(
    api.post<ApiResponse<RelayRoomStateResponse>>(`relay/rooms/${roomCode}/participants`),
  )

// POST /relay/rooms/{roomCode}/participants/kick — 릴레이 방 참여자 강퇴
// targetUserUuid는 강퇴 대상의 UUID(다른 사용자)이므로 body에 그대로 둔다.
export const postRelayRoomKick = (roomCode: string, targetUserUuid: string) =>
  apiUnwrap(
    api.post<ApiResponse<RelayRoomKickResponse>>(`relay/rooms/${roomCode}/participants/kick`, {
      targetUserUuid,
    }),
  )

// POST /relay/rooms/{roomCode}/close — 릴레이 방 수동 종료
export const postRelayRoomClose = (roomCode: string) =>
  apiUnwrap(api.post<ApiResponse<RelayRoomCloseResponse>>(`relay/rooms/${roomCode}/close`))

// POST /relay/rooms/{roomCode}/submissions — 릴레이 현재 파트 제출 (multipart)
interface PostRelayRoomSubmissionParams {
  roomCode: string
  canvasIndex: number
  part: string
  drawingImage: Blob
  hintImage?: Blob
}

export const postRelayRoomSubmission = ({
  roomCode,
  canvasIndex,
  part,
  drawingImage,
  hintImage,
}: PostRelayRoomSubmissionParams) => {
  const formData = new FormData()
  formData.append('drawingImage', drawingImage)
  if (hintImage) formData.append('hintImage', hintImage)

  return apiUnwrap(
    api.postForm<ApiResponse<RelayRoomSubmissionResponse>>(
      `relay/rooms/${roomCode}/submissions`,
      formData,
      { canvasIndex, part },
    ),
  )
}

// PATCH /relay/rooms/{roomCode}/settings — 릴레이 방 설정 변경
export const patchRelayRoomSettings = (roomCode: string, timeLimitSeconds: number) =>
  apiUnwrap(
    api.patch<ApiResponse<RelayRoomStateResponse>>(`relay/rooms/${roomCode}/settings`, {
      timeLimitSeconds,
    }),
  )

// GET /relay/rooms/{roomCode} — 릴레이 방 상태 조회
export const getRelayRoom = (roomCode: string) =>
  apiUnwrap(api.get<ApiResponse<RelayRoomStateResponse>>(`relay/rooms/${roomCode}`))

// GET /relay/rooms/{roomCode}/assignments/me — 릴레이 내 현재 배정 조회
export const getRelayRoomAssignmentMe = (roomCode: string) =>
  apiUnwrap(
    api.get<ApiResponse<RelayRoomMyAssignmentResponse>>(
      `relay/rooms/${roomCode}/assignments/me`,
    ),
  )

// DELETE /relay/rooms/{roomCode}/participants/me — 릴레이 방 자발적 퇴장
export const deleteRelayRoomParticipantMe = (roomCode: string) =>
  apiUnwrap(
    api.delete<ApiResponse<RelayRoomLeaveResponse>>(
      `relay/rooms/${roomCode}/participants/me`,
    ),
  )
