package com.nemonicworld.share.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.share.config.ShareProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * DB 없이 로그 분석에 사용할 수 있는 서명 기반 공유 토큰을 발급합니다.
 */
@Component
public class SignedShareTokenIssuer {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_VERSION = "1";
    private static final String PURPOSE_ARTIFACT_SHARE = "a";
    private static final String PURPOSE_COMMUNITY_MEMO_SHARE = "m";
    private static final String ARTIFACT_KIND_COMMUNITY_MEMO = "community_memo";
    private static final Map<String, String> ARTIFACT_KIND_CODES = Map.of("fortune", "f", "relay_drawing", "r",
        "flipbook", "b", "infinite_canvas", "i", "phone", "p", ARTIFACT_KIND_COMMUNITY_MEMO, "m");
    private static final Map<String, String> CHANNEL_CODES = Map.of("QR_DOWNLOAD", "d", "QR_SHARE", "s");

    private final ObjectMapper objectMapper;
    private final ShareProperties shareProperties;
    private final Base64.Encoder base64UrlEncoder = Base64.getUrlEncoder().withoutPadding();

    public SignedShareTokenIssuer(ObjectMapper objectMapper, ShareProperties shareProperties) {
        this.objectMapper = objectMapper;
        this.shareProperties = shareProperties;
    }

    public String issueArtifactToken(UUID artifactId, String artifactKind, String channel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("v", TOKEN_VERSION);
        payload.put("p", PURPOSE_ARTIFACT_SHARE);
        payload.put("id", encodeUuid(artifactId));
        payload.put("k", compactArtifactKind(artifactKind));
        payload.put("c", compactChannel(channel));

        String encodedPayload = encodePayload(payload);
        String signature = sign(encodedPayload);

        return "%s.%s".formatted(encodedPayload, signature);
    }

    public String issueCommunityMemoToken(UUID memoId, String channel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("v", TOKEN_VERSION);
        payload.put("p", PURPOSE_COMMUNITY_MEMO_SHARE);
        payload.put("id", encodeUuid(memoId));
        payload.put("k", compactArtifactKind(ARTIFACT_KIND_COMMUNITY_MEMO));
        payload.put("c", compactChannel(channel));

        String encodedPayload = encodePayload(payload);
        String signature = sign(encodedPayload);

        return "%s.%s".formatted(encodedPayload, signature);
    }

    private String encodeUuid(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 2);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());

        return base64UrlEncoder.encodeToString(buffer.array());
    }

    private String compactArtifactKind(String artifactKind) {
        return ARTIFACT_KIND_CODES.getOrDefault(artifactKind, artifactKind);
    }

    private String compactChannel(String channel) {
        return CHANNEL_CODES.getOrDefault(channel, channel);
    }

    private String encodePayload(Map<String, Object> payload) {
        try {
            return base64UrlEncoder.encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (JsonProcessingException e) {
            throw new InternalServerException("공유 토큰 페이로드 직렬화에 실패했습니다.", e);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(shareProperties.tokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));

            return base64UrlEncoder.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new InternalServerException("공유 토큰 서명에 실패했습니다.", e);
        }
    }
}
