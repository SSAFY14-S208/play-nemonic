// Invite 도메인 (OpenAPI: tag "Invite")

export interface InviteJoinResponse {
  boothType: string
  roomId: string
  roomName: string
  hostNickname: string
  currentParticipants: number
  maxParticipants: number
  yourRole: string
  alreadyJoined: boolean
}
