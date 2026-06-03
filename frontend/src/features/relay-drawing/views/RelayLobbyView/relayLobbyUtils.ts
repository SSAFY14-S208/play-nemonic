import type { RelayRoomParticipantResponse } from '@/shared/types'

interface CanStartRelayGameParams {
  isHost: boolean
  isStarting: boolean
  participants: RelayRoomParticipantResponse[]
  minParticipants: number
}

export function canStartRelayGame({
  isHost,
  isStarting,
  participants,
  minParticipants,
}: CanStartRelayGameParams) {
  const allConnected =
    participants.length > 0 && participants.every((participant) => participant.connected)

  return isHost && !isStarting && participants.length >= minParticipants && allConnected
}
