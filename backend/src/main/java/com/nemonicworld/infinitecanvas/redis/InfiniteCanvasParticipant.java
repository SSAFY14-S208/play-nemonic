package com.nemonicworld.infinitecanvas.redis;

import java.time.LocalDateTime;

public record InfiniteCanvasParticipant(String userUuid, String nickname, String color, String avatarUrl,
    boolean connected, LocalDateTime joinedAt, LocalDateTime lastConnectedAt, LocalDateTime updatedAt) {

    public InfiniteCanvasParticipant connect(LocalDateTime now) {
        return new InfiniteCanvasParticipant(userUuid, nickname, color, avatarUrl, true, joinedAt, now, now);
    }

    public InfiniteCanvasParticipant disconnect(LocalDateTime now) {
        return new InfiniteCanvasParticipant(userUuid, nickname, color, avatarUrl, false, joinedAt, lastConnectedAt,
            now);
    }

    public InfiniteCanvasParticipant updateProfile(String nickname, String color, String avatarUrl, LocalDateTime now) {
        return new InfiniteCanvasParticipant(userUuid, nickname, color, avatarUrl, connected, joinedAt, lastConnectedAt,
            now);
    }
}
