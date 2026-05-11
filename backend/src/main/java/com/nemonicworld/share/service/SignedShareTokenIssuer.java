package com.nemonicworld.share.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.share.config.ShareProperties;
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
    private static final String TOKEN_VERSION = "v1";
    private static final String PURPOSE_ARTIFACT_SHARE = "artifact_share";

    private final ObjectMapper objectMapper;
    private final ShareProperties shareProperties;
    private final Base64.Encoder base64UrlEncoder = Base64.getUrlEncoder().withoutPadding();

    public SignedShareTokenIssuer(ObjectMapper objectMapper, ShareProperties shareProperties) {
        this.objectMapper = objectMapper;
        this.shareProperties = shareProperties;
    }

    public String issueArtifactToken(UUID artifactId, String artifactKind, String channel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", TOKEN_VERSION);
        payload.put("purpose", PURPOSE_ARTIFACT_SHARE);
        payload.put("artifactId", artifactId.toString());
        payload.put("artifactKind", artifactKind);
        payload.put("channel", channel);

        String encodedPayload = encodePayload(payload);
        String signature = sign(encodedPayload);

        return "%s.%s".formatted(encodedPayload, signature);
    }

    private String encodePayload(Map<String, Object> payload) {
        try {
            return base64UrlEncoder.encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (JsonProcessingException e) {
            throw new InternalServerException("Share token payload serialization failed.", e);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(shareProperties.tokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));

            return base64UrlEncoder.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new InternalServerException("Share token signing failed.", e);
        }
    }
}
