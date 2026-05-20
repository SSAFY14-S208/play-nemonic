package com.nemonicworld.support;

import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.auth.service.AdminTokenStore;
import com.nemonicworld.auth.service.IssuedAdminRefreshToken;
import com.nemonicworld.auth.service.StoredAdminRefreshToken;
import com.nemonicworld.common.jwt.AdminTokenClaims;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 통합 테스트에서 {@link AdminTokenStore} 의 in-memory 대체 구현이다.
 *
 * <p>
 * {@link com.nemonicworld.auth.repository.RedisAdminTokenStore} 가 실제 Redis 인스턴스에 연결하려고
 * 시도하면 테스트 환경에서 {@code RedisConnectionFailureException} 으로 인증 필터가 401 을 반환한다.
 * H2 in-memory DB 가 컨텍스트별로 격리되면서 한 컨텍스트가 만든 token state 를 다른 컨텍스트가 빌어 쓰는
 * 우연한 통과 동작도 사라졌다. 모든 통합 테스트가 이 in-memory 구현을 {@code @Primary} 빈으로 받아
 * Redis 연결 의존을 제거한다.
 * </p>
 */
public class InMemoryAdminTokenStore implements AdminTokenStore {

    private final Map<String, StoredAdminRefreshToken> refreshTokens = new ConcurrentHashMap<>();
    private final Set<String> accessTokenBlacklist = ConcurrentHashMap.newKeySet();
    private final Map<Long, Instant> accessRevokedAfter = new ConcurrentHashMap<>();

    @Override
    public IssuedAdminRefreshToken issueRefreshToken(AdminUser adminUser) {
        String refreshToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(14 * 24 * 60 * 60);
        refreshTokens.put(refreshToken, new StoredAdminRefreshToken(adminUser.getId(), expiresAt));

        return new IssuedAdminRefreshToken(refreshToken, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    @Override
    public Optional<StoredAdminRefreshToken> findRefreshToken(String refreshToken) {
        StoredAdminRefreshToken storedToken = refreshTokens.get(refreshToken);
        if (storedToken == null || storedToken.isExpired(Instant.now())) {
            return Optional.empty();
        }

        return Optional.of(storedToken);
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        refreshTokens.remove(refreshToken);
    }

    @Override
    public void revokeAllRefreshTokens(Long adminId) {
        refreshTokens.entrySet().removeIf(entry -> entry.getValue().adminId().equals(adminId));
    }

    @Override
    public void blacklistAccessToken(AdminTokenClaims claims) {
        if (claims.tokenId() != null) {
            accessTokenBlacklist.add(claims.tokenId());
        }
    }

    @Override
    public void revokeAccessTokensIssuedBefore(Long adminId, Instant revokedAt) {
        accessRevokedAfter.put(adminId, revokedAt);
    }

    @Override
    public boolean isAccessTokenRevoked(AdminTokenClaims claims) {
        if (claims.tokenId() != null && accessTokenBlacklist.contains(claims.tokenId())) {
            return true;
        }

        Instant revokedAfter = accessRevokedAfter.get(claims.adminId());
        return revokedAfter != null && !claims.issuedAt().isAfter(revokedAfter);
    }

    public void clear() {
        refreshTokens.clear();
        accessTokenBlacklist.clear();
        accessRevokedAfter.clear();
    }
}
