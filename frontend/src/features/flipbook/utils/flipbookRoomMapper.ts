import type {
  DrawingLine,
  FlipbookAssignmentResponse,
  FlipbookRoomParticipantResponse,
  FlipbookRoomStateResponse,
} from '@/shared/types'
import { getDisplayImageUrl } from '@/shared/utils'
import { FLIPBOOK_TIME_LIMITS_SECONDS } from '../constants'
import type { FlipbookParticipant, FlipbookTimeLimitSeconds } from '../types'

export function isFlipbookTimeLimitSeconds(
  seconds: number,
): seconds is FlipbookTimeLimitSeconds {
  return FLIPBOOK_TIME_LIMITS_SECONDS.includes(seconds as FlipbookTimeLimitSeconds)
}

export function toFlipbookTimeLimitSeconds(seconds: number): FlipbookTimeLimitSeconds {
  return isFlipbookTimeLimitSeconds(seconds) ? seconds : FLIPBOOK_TIME_LIMITS_SECONDS[1]
}

export function toFlipbookParticipant(
  participant: FlipbookRoomParticipantResponse,
  currentUserUuid: string | null,
): FlipbookParticipant {
  return {
    id: participant.userUuid,
    userUuid: participant.userUuid,
    name: `${participant.nickname}${participant.userUuid === currentUserUuid ? ' (나)' : ''}`,
    avatar: participant.host ? '👑' : '🙂',
    isHost: participant.host,
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

export function getDrawingTurnCount(cycleRoundCount: number | null, participantCount: number) {
  if (cycleRoundCount === null) return null

  return cycleRoundCount * Math.max(1, participantCount)
}

export function getAssignmentKey(assignment: FlipbookAssignmentResponse) {
  return `${assignment.currentRound}:${assignment.flipbookIndex}:${assignment.frameIndex}`
}
