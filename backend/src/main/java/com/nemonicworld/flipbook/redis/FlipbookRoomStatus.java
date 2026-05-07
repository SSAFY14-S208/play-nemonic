package com.nemonicworld.flipbook.redis;

/**
 * Redis에 저장되는 플립북 방의 진행 상태입니다.
 */
public enum FlipbookRoomStatus {
    WAITING, PLAYING, FINISHED, CLOSED
}
