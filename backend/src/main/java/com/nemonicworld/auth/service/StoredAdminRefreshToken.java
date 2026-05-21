package com.nemonicworld.auth.service;

import java.time.Instant;

public record StoredAdminRefreshToken(Long adminId, Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
