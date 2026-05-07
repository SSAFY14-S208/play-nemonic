package com.nemonicworld.common.jwt;

import com.nemonicworld.admin.entity.AdminRole;
import java.time.Instant;

public record AdminTokenClaims(Long adminId, String loginId, AdminRole role, String tokenId, Instant issuedAt,
    Instant expiresAt) {
}
