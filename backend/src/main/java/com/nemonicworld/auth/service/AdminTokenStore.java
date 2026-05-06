package com.nemonicworld.auth.service;

import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.common.jwt.AdminTokenClaims;
import java.time.Instant;
import java.util.Optional;

public interface AdminTokenStore {

    IssuedAdminRefreshToken issueRefreshToken(AdminUser adminUser);

    Optional<StoredAdminRefreshToken> findRefreshToken(String refreshToken);

    void revokeRefreshToken(String refreshToken);

    void revokeAllRefreshTokens(Long adminId);

    void blacklistAccessToken(AdminTokenClaims claims);

    void revokeAccessTokensIssuedBefore(Long adminId, Instant revokedAt);

    boolean isAccessTokenRevoked(AdminTokenClaims claims);
}
