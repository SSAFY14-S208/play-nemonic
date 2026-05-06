package com.nemonicworld.relay.dto.response;

/**
 * 방 상태 조회에서 현재 요청자가 입장 또는 재접속할 수 없는 이유입니다.
 */
public enum RelayRoomViewerBlockedReason {
    ROOM_FULL, GAME_IN_PROGRESS, RECONNECT_EXPIRED, KICKED, ROOM_FINISHED, ROOM_CLOSED
}
