package com.nemonicworld.common.jwt;

import java.time.OffsetDateTime;

public record IssuedAdminToken(String accessToken, String tokenId, OffsetDateTime expiresAt) {
}
