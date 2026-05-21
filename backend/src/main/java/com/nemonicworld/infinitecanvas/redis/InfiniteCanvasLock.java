package com.nemonicworld.infinitecanvas.redis;

import java.time.LocalDateTime;

public record InfiniteCanvasLock(String elementId, String userUuid, LocalDateTime lockedAt, LocalDateTime expiresAt) {

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
