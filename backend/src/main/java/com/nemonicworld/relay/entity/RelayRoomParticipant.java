package com.nemonicworld.relay.entity;

import java.time.LocalDateTime;

public record RelayRoomParticipant(String userUuid, String nickname, boolean host, int joinOrder, boolean connected,
    LocalDateTime joinedAt) {
}
