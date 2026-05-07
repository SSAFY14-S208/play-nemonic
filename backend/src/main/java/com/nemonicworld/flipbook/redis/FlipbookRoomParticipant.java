package com.nemonicworld.flipbook.redis;

import java.time.LocalDateTime;

/**
 * Redis 플립북 방 상태에 저장되는 참여자 1명의 상태입니다.
 */
public record FlipbookRoomParticipant(String userUuid, String nickname, boolean host, int joinOrder, boolean connected,
    LocalDateTime disconnectedAt, LocalDateTime joinedAt) {
}
