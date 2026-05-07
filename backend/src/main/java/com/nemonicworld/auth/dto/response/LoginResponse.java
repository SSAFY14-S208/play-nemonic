package com.nemonicworld.auth.dto.response;

import com.nemonicworld.admin.dto.response.AdminResponse;
import java.time.OffsetDateTime;

public record LoginResponse(String accessToken, String tokenType, OffsetDateTime expiresAt, String refreshToken,
    OffsetDateTime refreshTokenExpiresAt, AdminResponse admin) {
}
