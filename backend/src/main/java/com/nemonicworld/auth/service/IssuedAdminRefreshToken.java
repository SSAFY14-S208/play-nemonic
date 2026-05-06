package com.nemonicworld.auth.service;

import java.time.OffsetDateTime;

public record IssuedAdminRefreshToken(String refreshToken, OffsetDateTime expiresAt) {
}
