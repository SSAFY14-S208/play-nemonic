package com.nemonicworld.relay.redis;

import java.time.LocalDateTime;

/**
 * Redis 릴레이 방 상태에 저장되는 참여자 1명의 상태입니다.
 */
public record RelayRoomParticipant(String userUuid, String nickname, boolean host, int joinOrder, boolean connected,
    LocalDateTime disconnectedAt, LocalDateTime joinedAt, boolean dropped, LocalDateTime droppedAt) {

    public RelayRoomParticipant {
        if (!dropped) {
            droppedAt = null;
        }
    }

    public RelayRoomParticipant(String userUuid, String nickname, boolean host, int joinOrder, boolean connected,
        LocalDateTime disconnectedAt, LocalDateTime joinedAt) {
        this(userUuid, nickname, host, joinOrder, connected, disconnectedAt, joinedAt, false, null);
    }

    public RelayRoomParticipant withConnection(boolean connected, LocalDateTime disconnectedAt) {
        return new RelayRoomParticipant(userUuid, nickname, host, joinOrder, connected, disconnectedAt, joinedAt,
            dropped, droppedAt);
    }

    public RelayRoomParticipant withHost(boolean host) {
        return new RelayRoomParticipant(userUuid, nickname, host, joinOrder, connected, disconnectedAt, joinedAt,
            dropped, droppedAt);
    }

    public RelayRoomParticipant drop(LocalDateTime droppedAt) {
        return new RelayRoomParticipant(userUuid, nickname, host, joinOrder, false, disconnectedAt, joinedAt, true,
            droppedAt);
    }
}
