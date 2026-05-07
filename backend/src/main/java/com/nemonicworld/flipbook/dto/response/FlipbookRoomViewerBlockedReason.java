package com.nemonicworld.flipbook.dto.response;

/**
 * 플립북 대기방 조회에서 현재 요청자가 입장할 수 없는 이유입니다.
 */
public enum FlipbookRoomViewerBlockedReason {
    ROOM_FULL, GAME_IN_PROGRESS, KICKED, ROOM_FINISHED, ROOM_CLOSED
}
