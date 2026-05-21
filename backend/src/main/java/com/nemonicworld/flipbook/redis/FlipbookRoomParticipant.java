package com.nemonicworld.flipbook.redis;

import java.time.LocalDateTime;

/**
 * Redis 플립북 방 상태에 저장되는 참여자 1명의 상태입니다.
 */
public record FlipbookRoomParticipant(String userUuid, String nickname, boolean host, int joinOrder, boolean connected,
    LocalDateTime disconnectedAt, LocalDateTime joinedAt, boolean dropped, LocalDateTime droppedAt) {

    public FlipbookRoomParticipant {
        if (!dropped) {
            droppedAt = null;
        }
    }

    public FlipbookRoomParticipant(String userUuid, String nickname, boolean host, int joinOrder, boolean connected,
        LocalDateTime disconnectedAt, LocalDateTime joinedAt) {
        this(userUuid, nickname, host, joinOrder, connected, disconnectedAt, joinedAt, false, null);
    }

    public FlipbookRoomParticipant withConnection(boolean connected, LocalDateTime disconnectedAt) {
        return new FlipbookRoomParticipant(userUuid, nickname, host, joinOrder, connected, disconnectedAt, joinedAt,
            dropped, droppedAt);
    }

    public FlipbookRoomParticipant withHost(boolean host) {
        return new FlipbookRoomParticipant(userUuid, nickname, host, joinOrder, connected, disconnectedAt, joinedAt,
            dropped, droppedAt);
    }

    public FlipbookRoomParticipant drop(LocalDateTime droppedAt) {
        return new FlipbookRoomParticipant(userUuid, nickname, host, joinOrder, false, disconnectedAt, joinedAt, true,
            droppedAt);
    }
}
