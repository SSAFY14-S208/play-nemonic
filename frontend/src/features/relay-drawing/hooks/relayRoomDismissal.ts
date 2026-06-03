// 종료성 WS 이벤트(강퇴, 방 종료 등)로 인한 퇴장을 추적해
// 자발적 퇴장 감지 effect가 중복 DELETE API를 호출하지 않도록 방지한다.
// 모듈 레벨 Set이라 여러 훅에서 임포트해도 동일 인스턴스가 공유된다.

const dismissedRoomCodes = new Set<string>()

export function markRoomDismissed(roomCode: string | null): void {
  if (!roomCode) return
  dismissedRoomCodes.add(roomCode)
}

export function hasRoomDismissed(roomCode: string | null): boolean {
  return roomCode !== null && dismissedRoomCodes.has(roomCode)
}

export function resetRoomDismissed(roomCode: string | null): void {
  if (!roomCode) return
  dismissedRoomCodes.delete(roomCode)
}
