import { HTTPError } from 'ky'

import {
  ApiError,
  getRelayRoom,
  getRelayRoomResults,
  postInvite,
} from '@/shared/apis'
import type {
  RelayBlockedReason,
  RelayRoomStateResponse,
  RelayRoomStatus,
} from '@/shared/types'

import { PART_TO_ROUND_KEY } from '@/features/relay-drawing/constants'
import { useRelayDrawingStore } from '@/features/relay-drawing/stores'

const BLOCKED_REASON_MESSAGE: Record<RelayBlockedReason, string> = {
  ROOM_FULL: '방 정원이 가득 차 입장할 수 없습니다.',
  GAME_IN_PROGRESS: '이미 릴레이 드로잉이 진행 중입니다.',
  RECONNECT_EXPIRED: '재접속 가능 시간이 만료되었습니다.',
  KICKED: '내보내진 방에는 다시 입장할 수 없습니다.',
  ROOM_FINISHED: '이미 종료된 방입니다.',
  ROOM_CLOSED: '닫힌 방입니다.',
}

export function getNonParticipantBlockedReason(
  roomStatus: RelayRoomStatus,
): RelayBlockedReason | null {
  if (roomStatus === 'PLAYING' || roomStatus === 'FINALIZING') {
    return 'GAME_IN_PROGRESS'
  }
  if (roomStatus === 'FINISHED') {
    return 'ROOM_FINISHED'
  }
  if (roomStatus === 'CLOSED') {
    return 'ROOM_CLOSED'
  }
  return null
}

export function getBlockedRoomMessage(reason: RelayBlockedReason) {
  return BLOCKED_REASON_MESSAGE[reason] ?? '입장할 수 없는 방입니다.'
}

export function getHydrationErrorMessage(caughtError: unknown) {
  if (caughtError instanceof ApiError) return caughtError.message

  if (
    caughtError instanceof HTTPError &&
    caughtError.response.status === 404
  ) {
    return '존재하지 않는 방입니다.'
  }

  return '방 정보를 불러오지 못했습니다.'
}

export function getBlockedReasonForRoom(room: RelayRoomStateResponse) {
  if (room.viewer.participant) return null

  return getNonParticipantBlockedReason(room.status) ?? room.viewer.blockedReason
}

export function shouldRedirectBlockedViewer(room: RelayRoomStateResponse) {
  const blockedReason = getBlockedReasonForRoom(room)
  return blockedReason !== null || !room.viewer.canJoin
}

export async function joinWaitingRoom(roomCode: string) {
  await postInvite(roomCode)
  return getRelayRoom(roomCode)
}

export function syncPlayingRoomState(room: RelayRoomStateResponse) {
  if (room.status !== 'PLAYING') return

  const store = useRelayDrawingStore.getState()
  store.setPartDeadlineAt(room.partDeadlineAt)

  const roundKey = PART_TO_ROUND_KEY[room.currentPart]
  store.setRoundDeadline(roundKey, room.partDeadlineAt)

  if (store.partFetchTrigger === 0) {
    store.incrementPartFetchTrigger()
  }
}

export async function syncFinishedRoomResults(roomCode: string) {
  try {
    const resultResponse = await getRelayRoomResults(roomCode)
    useRelayDrawingStore.getState().setResults(resultResponse.results)
  } catch {
    // Result view fetches result data independently, so hydration can ignore this.
  }
}
