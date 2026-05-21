package com.nemonicworld.infinitecanvas.redis;

import java.time.LocalDateTime;

public record InfiniteCanvasParticipant(String userUuid, String nickname, String color, String avatarUrl, boolean host,
    boolean connected, LocalDateTime joinedAt, LocalDateTime lastConnectedAt, LocalDateTime updatedAt) {

    public InfiniteCanvasParticipant connect(LocalDateTime now) {
        return new InfiniteCanvasParticipant(userUuid, nickname, color, avatarUrl, host, true, joinedAt, now, now);
    }

    public InfiniteCanvasParticipant disconnect(LocalDateTime now) {
        return new InfiniteCanvasParticipant(userUuid, nickname, color, avatarUrl, host, false, joinedAt,
            lastConnectedAt, now);
    }

    public InfiniteCanvasParticipant updateProfile(String nickname, String color, String avatarUrl, LocalDateTime now) {
        return new InfiniteCanvasParticipant(userUuid, nickname, color, avatarUrl, host, connected, joinedAt,
            lastConnectedAt, now);
    }

    public InfiniteCanvasParticipant withHost(boolean host) {
        return new InfiniteCanvasParticipant(userUuid, nickname, color, avatarUrl, host, connected, joinedAt,
            lastConnectedAt, updatedAt);
    }
}
