package com.nemonicworld.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.auth.service.AdminTokenStore;
import com.nemonicworld.auth.service.IssuedAdminRefreshToken;
import com.nemonicworld.auth.service.StoredAdminRefreshToken;
import com.nemonicworld.common.jwt.AdminTokenClaims;
import com.nemonicworld.common.jwt.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Repository
public class RedisAdminTokenStore implements AdminTokenStore {

    private static final int REFRESH_TOKEN_BYTE_LENGTH = 32;
    private static final String REFRESH_KEY_PREFIX = "admin:refresh:";
    private static final String REFRESH_INDEX_KEY_PREFIX = "admin:refresh:index:";
    private static final String ACCESS_BLACKLIST_KEY_PREFIX = "admin:access:blacklist:";
    private static final String ACCESS_REVOKED_AFTER_KEY_PREFIX = "admin:access:revoked-after:";
    private static final String REVOKED_VALUE = "revoked";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder base64UrlEncoder = Base64.getUrlEncoder().withoutPadding();

    public RedisAdminTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
        JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public IssuedAdminRefreshToken issueRefreshToken(AdminUser adminUser) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.getRefreshTokenExpiration());
        String refreshToken = generateRefreshToken();
        String tokenHash = hashRefreshToken(refreshToken);
        Duration ttl = Duration.between(now, expiresAt);

        redisTemplate.opsForValue().set(createRefreshKey(tokenHash),
            serialize(new StoredAdminRefreshToken(adminUser.getId(), expiresAt)), ttl);
        redisTemplate.opsForSet().add(createRefreshIndexKey(adminUser.getId()), tokenHash);
        redisTemplate.expire(createRefreshIndexKey(adminUser.getId()), jwtProperties.getRefreshTokenExpiration());

        return new IssuedAdminRefreshToken(refreshToken, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    @Override
    public Optional<StoredAdminRefreshToken> findRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return Optional.empty();
        }

        String tokenHash = hashRefreshToken(refreshToken);
        String refreshTokenValue = redisTemplate.opsForValue().get(createRefreshKey(tokenHash));
        if (!StringUtils.hasText(refreshTokenValue)) {
            return Optional.empty();
        }

        StoredAdminRefreshToken storedToken = deserialize(refreshTokenValue);
        if (storedToken.isExpired(Instant.now())) {
            deleteRefreshToken(tokenHash, storedToken);
            return Optional.empty();
        }

        return Optional.of(storedToken);
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }

        String tokenHash = hashRefreshToken(refreshToken);
        String refreshTokenValue = redisTemplate.opsForValue().get(createRefreshKey(tokenHash));
        if (!StringUtils.hasText(refreshTokenValue)) {
            redisTemplate.delete(createRefreshKey(tokenHash));
            return;
        }

        deleteRefreshToken(tokenHash, deserialize(refreshTokenValue));
    }

    @Override
    public void revokeAllRefreshTokens(Long adminId) {
        String indexKey = createRefreshIndexKey(adminId);
        Set<String> tokenHashes = redisTemplate.opsForSet().members(indexKey);
        if (!CollectionUtils.isEmpty(tokenHashes)) {
            Collection<String> refreshKeys = tokenHashes.stream().map(this::createRefreshKey).toList();
            redisTemplate.delete(refreshKeys);
        }
        redisTemplate.delete(indexKey);
    }

    @Override
    public void blacklistAccessToken(AdminTokenClaims claims) {
        if (!StringUtils.hasText(claims.tokenId())) {
            return;
        }

        Duration ttl = Duration.between(Instant.now(), claims.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }

        redisTemplate.opsForValue().set(createAccessBlacklistKey(claims.tokenId()), REVOKED_VALUE, ttl);
    }

    @Override
    public void revokeAccessTokensIssuedBefore(Long adminId, Instant revokedAt) {
        redisTemplate.opsForValue().set(createAccessRevokedAfterKey(adminId),
            String.valueOf(revokedAt.getEpochSecond()), jwtProperties.getAccessTokenExpiration());
    }

    @Override
    public boolean isAccessTokenRevoked(AdminTokenClaims claims) {
        if (StringUtils.hasText(claims.tokenId())
            && Boolean.TRUE.equals(redisTemplate.hasKey(createAccessBlacklistKey(claims.tokenId())))) {
            return true;
        }

        String revokedAfterValue = redisTemplate.opsForValue().get(createAccessRevokedAfterKey(claims.adminId()));
        if (!StringUtils.hasText(revokedAfterValue)) {
            return false;
        }

        Instant revokedAfter = Instant.ofEpochSecond(Long.parseLong(revokedAfterValue));
        return !claims.issuedAt().isAfter(revokedAfter);
    }

    private void deleteRefreshToken(String tokenHash, StoredAdminRefreshToken storedToken) {
        redisTemplate.delete(createRefreshKey(tokenHash));
        redisTemplate.opsForSet().remove(createRefreshIndexKey(storedToken.adminId()), tokenHash);
    }

    private String generateRefreshToken() {
        byte[] tokenBytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);

        return base64UrlEncoder.encodeToString(tokenBytes);
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return base64UrlEncoder.encodeToString(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Refresh token hashing failed.", e);
        }
    }

    private String createRefreshKey(String tokenHash) {
        return REFRESH_KEY_PREFIX + tokenHash;
    }

    private String createRefreshIndexKey(Long adminId) {
        return REFRESH_INDEX_KEY_PREFIX + adminId;
    }

    private String createAccessBlacklistKey(String tokenId) {
        return ACCESS_BLACKLIST_KEY_PREFIX + tokenId;
    }

    private String createAccessRevokedAfterKey(Long adminId) {
        return ACCESS_REVOKED_AFTER_KEY_PREFIX + adminId;
    }

    private String serialize(StoredAdminRefreshToken storedToken) {
        try {
            return objectMapper.writeValueAsString(storedToken);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Refresh token serialization failed.", e);
        }
    }

    private StoredAdminRefreshToken deserialize(String refreshTokenValue) {
        try {
            return objectMapper.readValue(refreshTokenValue, StoredAdminRefreshToken.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Refresh token deserialization failed.", e);
        }
    }
}
