package com.nemonicworld.common.jwt;

import com.nemonicworld.auth.entity.AdminRole;
import java.time.Instant;

public record AdminTokenClaims(Long adminId, String loginId, AdminRole role, Instant expiresAt) {
}
