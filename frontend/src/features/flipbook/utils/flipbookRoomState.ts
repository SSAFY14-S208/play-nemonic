import type {
  FlipbookBlockedReason,
  FlipbookRoomCreateResponse,
  FlipbookRoomStateResponse,
} from '@/shared/types'

export const BLOCKED_REASON_MESSAGE: Record<FlipbookBlockedReason, string> = {
  ROOM_FULL: '정원이 가득 찬 플립북 방입니다.',
  GAME_IN_PROGRESS: '이미 게임이 진행 중인 방입니다.',
  KICKED: '방에서 내보내져 다시 입장할 수 없습니다.',
  RECONNECT_EXPIRED: '재접속 가능 시간이 지나 입장할 수 없습니다.',
  ROOM_FINISHED: '이미 종료된 플립북 방입니다.',
  ROOM_CLOSED: '종료된 플립북 방입니다.',
}

export function createRoomStateFromCreateResponse({
  createdRoom,
  userUuid,
}: {
  createdRoom: FlipbookRoomCreateResponse
  userUuid: string | null
}): FlipbookRoomStateResponse {
  const viewerUserUuid = userUuid ?? createdRoom.hostUserUuid
  const isViewerHost = createdRoom.hostUserUuid === viewerUserUuid
  const canStart =
    createdRoom.status === 'WAITING' &&
    isViewerHost &&
    createdRoom.participantCount >= createdRoom.minParticipants

  return {
    roomCode: createdRoom.roomCode,
    status: createdRoom.status,
    hostUserUuid: createdRoom.hostUserUuid,
    timeLimitSeconds: createdRoom.timeLimitSeconds,
    allowedTimeLimitSeconds: createdRoom.allowedTimeLimitSeconds,
    timeLimitOptions: createdRoom.timeLimitOptions,
    timeLimitSecondsOptions: createdRoom.timeLimitSecondsOptions,
    minParticipants: createdRoom.minParticipants,
    maxParticipants: createdRoom.maxParticipants,
    participantCount: createdRoom.participantCount,
    currentRound: null,
    totalRounds: null,
    roundStartedAt: null,
    roundDeadlineAt: null,
    gameStartedAt: null,
    participants: createdRoom.participants,
    viewer: {
      userUuid: viewerUserUuid,
      participant: true,
      host: isViewerHost,
      canJoin: false,
      canStart,
      blockedReason: null,
    },
    createdAt: createdRoom.createdAt,
    updatedAt: createdRoom.createdAt,
  }
}
