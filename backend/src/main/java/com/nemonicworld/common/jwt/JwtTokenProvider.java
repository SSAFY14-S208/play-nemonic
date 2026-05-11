package com.nemonicworld.common.jwt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.admin.entity.AdminRole;
import com.nemonicworld.admin.entity.AdminUser;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.common.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtTokenProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_TYPE = "JWT";
    private static final String JWT_ALGORITHM = "HS256";
    private static final String INVALID_TOKEN_MESSAGE = "인증이 필요합니다.";

    private final ObjectMapper objectMapper;
    private final JwtProperties jwtProperties;
    private final Base64.Encoder base64UrlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder base64UrlDecoder = Base64.getUrlDecoder();

    public JwtTokenProvider(ObjectMapper objectMapper, JwtProperties jwtProperties) {
        this.objectMapper = objectMapper;
        this.jwtProperties = jwtProperties;
    }

    public IssuedAdminToken createAccessToken(AdminUser adminUser) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwtProperties.getAccessTokenExpiration());
        String tokenId = UUID.randomUUID().toString();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", JWT_ALGORITHM);
        header.put("typ", TOKEN_TYPE);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", adminUser.getId().toString());
        payload.put("login_id", adminUser.getLoginId());
        payload.put("role", adminUser.getRole().getValue());
        payload.put("jti", tokenId);
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String unsignedToken = "%s.%s".formatted(encodedHeader, encodedPayload);
        String signature = sign(unsignedToken);

        return new IssuedAdminToken("%s.%s".formatted(unsignedToken, signature), tokenId,
            OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    public AdminTokenClaims parseAccessToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }

        String unsignedToken = "%s.%s".formatted(parts[0], parts[1]);
        String expectedSignature = sign(unsignedToken);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
            parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }

        JsonNode payload = decodePayload(parts[1]);
        Instant issuedAt = Instant.ofEpochSecond(payload.path("iat").asLong());
        Instant expiresAt = Instant.ofEpochSecond(payload.path("exp").asLong());
        if (!expiresAt.isAfter(Instant.now())) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }
        String tokenId = payload.path("jti").asText(null);
        if (!StringUtils.hasText(tokenId)) {
            tokenId = null;
        }

        return new AdminTokenClaims(payload.path("sub").asLong(), payload.path("login_id").asText(),
            AdminRole.fromValue(payload.path("role").asText()), tokenId, issuedAt, expiresAt);
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return base64UrlEncoder.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            throw new InternalServerException("JWT payload serialization failed.", e);
        }
    }

    private JsonNode decodePayload(String encodedPayload) {
        try {
            return objectMapper.readTree(base64UrlDecoder.decode(encodedPayload));
        } catch (Exception e) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return base64UrlEncoder.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new InternalServerException("JWT signing failed.", e);
        }
    }
}
