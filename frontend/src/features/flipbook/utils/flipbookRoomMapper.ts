import type {
  DrawingLine,
  FlipbookAssignmentResponse,
  FlipbookRoomCreateResponse,
  FlipbookRoomParticipantResponse,
  FlipbookRoomStateResponse,
} from '@/shared/types'
import { getDisplayImageUrl } from '@/shared/utils'
import type { FlipbookParticipant, FlipbookTimeLimitSeconds } from '../types'

const LOCAL_PARTICIPANT_FALLBACK: FlipbookParticipant = {
  id: 'local-flipbook-user',
  userUuid: 'local-flipbook-user',
  name: '나',
  avatar: '🙂',
  isHost: true,
}

export function isFlipbookTimeLimitSeconds(
  seconds: number,
): seconds is FlipbookTimeLimitSeconds {
  return Number.isInteger(seconds) && seconds > 0
}

export function toFlipbookTimeLimitSeconds(
  seconds: number,
  fallback: FlipbookTimeLimitSeconds = 45,
): FlipbookTimeLimitSeconds {
  return isFlipbookTimeLimitSeconds(seconds) ? seconds : fallback
}

export function getFlipbookTimeLimitOptions(
  roomState: FlipbookRoomCreateResponse | FlipbookRoomStateResponse | null,
) {
  if (!roomState) return []

  const roomStateRecord = roomState as unknown as Record<string, unknown>
  const serverOptionsPayload =
    roomState.allowedTimeLimitSeconds ??
    roomState.timeLimitOptions ??
    roomState.timeLimitSecondsOptions ??
    roomStateRecord.allowedTimeLimitSecondsList ??
    roomStateRecord.allowedTimeLimits ??
    roomStateRecord.allowedTimeLimitsSeconds ??
    roomStateRecord.timeLimitSecondsAllowed ??
    roomStateRecord.availableTimeLimitSeconds ??
    roomStateRecord.availableTimeLimitSecondsList ??
    roomStateRecord.availableTimeLimits ??
    []

  const normalizedServerOptions = Array.isArray(serverOptionsPayload)
    ? serverOptionsPayload
    : typeof serverOptionsPayload === 'object' &&
        serverOptionsPayload !== null &&
        Array.isArray((serverOptionsPayload as { allowed?: unknown }).allowed)
      ? (serverOptionsPayload as { allowed: unknown[] }).allowed
      : []
  const options = normalizedServerOptions.filter(isFlipbookTimeLimitSeconds)

  if (options.length > 0) {
    return Array.from(new Set(options))
  }

  return isFlipbookTimeLimitSeconds(roomState.timeLimitSeconds)
    ? [roomState.timeLimitSeconds]
    : []
}

export function toFlipbookParticipant(
  participant: FlipbookRoomParticipantResponse,
): FlipbookParticipant {
  return {
    id: participant.userUuid,
    userUuid: participant.userUuid,
    name: participant.nickname,
    avatar: participant.host ? '👑' : '🙂',
    isHost: participant.host,
    isConnected: participant.connected,
  }
}

export function createLocalFlipbookParticipant({
  nickname,
  userUuid,
}: {
  nickname: string | null
  userUuid: string | null
}): FlipbookParticipant {
  return {
    ...LOCAL_PARTICIPANT_FALLBACK,
    userUuid: userUuid ?? LOCAL_PARTICIPANT_FALLBACK.userUuid,
    name: nickname ? `${nickname} (나)` : LOCAL_PARTICIPANT_FALLBACK.name,
    isHost: true,
  }
}

export function createPreviousFrameLinesFromAssignment(
  assignment: FlipbookAssignmentResponse,
): DrawingLine[] {
  const hint = assignment.hint
  const hintImageUrl = hint?.imageUrl ?? hint?.url
  if (!hint || !hintImageUrl || hint.empty) return []

  return [
    {
      id: `flipbook-hint-${hint.flipbookIndex}-${hint.frameIndex}-${hint.round}`,
      kind: 'fill',
      color: 'transparent',
      strokeWidth: 0,
      points: [],
      imageDataUrl: getDisplayImageUrl(hintImageUrl) ?? hintImageUrl,
    },
  ]
}

export function getServerRoundCount({
  roomState,
  fallback,
}: {
  roomState?: FlipbookRoomStateResponse | null
  fallback?: number | null
}) {
  return roomState?.totalRounds ?? fallback ?? null
}

export function getRoomParticipantCount(roomState: FlipbookRoomStateResponse | null) {
  if (!roomState) return 1

  return Math.max(1, roomState.participantCount, roomState.participants.length)
}

export function getAssignmentKey(assignment: FlipbookAssignmentResponse) {
  return `${assignment.currentRound}:${assignment.flipbookIndex}:${assignment.frameIndex}`
}
