package com.nemonicworld.auth.dto.response;

import java.time.OffsetDateTime;

public record AdminLoginResponse(String accessToken, String tokenType, OffsetDateTime expiresAt, String refreshToken,
    OffsetDateTime refreshTokenExpiresAt, AdminResponse admin) {
}
