package com.nemonicworld.relay.redis;

import java.time.LocalDateTime;

/**
 * Redis 릴레이 방 상태에 저장되는 참여자 1명의 상태입니다.
 */
public record RelayRoomParticipant(String userUuid, String nickname, boolean host, int joinOrder, boolean connected,
    LocalDateTime disconnectedAt, LocalDateTime joinedAt) {
}
