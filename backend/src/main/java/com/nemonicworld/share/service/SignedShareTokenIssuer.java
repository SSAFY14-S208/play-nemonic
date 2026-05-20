package com.nemonicworld.share.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.common.exception.InternalServerException;
import com.nemonicworld.share.config.ShareProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
    private static final String COMMUNITY_MEMO_SHARE_CODE = "M";
    private static final int SIGNATURE_BYTES = 6;

    private final ShareProperties shareProperties;
    private final Base64.Encoder base64UrlEncoder = Base64.getUrlEncoder().withoutPadding();

    public SignedShareTokenIssuer(ObjectMapper objectMapper, ShareProperties shareProperties) {
        this.shareProperties = shareProperties;
    }

    public String issueArtifactToken(UUID artifactId, String artifactKind, String channel) {
        String encodedPayload = "%s%s%s".formatted(TOKEN_VERSION, artifactShareCode(artifactKind, channel),
            encodeUuid(artifactId));
        String signature = sign(encodedPayload);

        return "%s.%s".formatted(encodedPayload, signature);
    }

    public String issueCommunityMemoToken(UUID memoId, String channel) {
        String encodedPayload = "%s%s%s".formatted(TOKEN_VERSION, communityMemoShareCode(channel), encodeUuid(memoId));
        String signature = sign(encodedPayload);

        return "%s.%s".formatted(encodedPayload, signature);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(shareProperties.tokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));

            byte[] signature = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] truncatedSignature = new byte[SIGNATURE_BYTES];
            System.arraycopy(signature, 0, truncatedSignature, 0, truncatedSignature.length);

            return base64UrlEncoder.encodeToString(truncatedSignature);
        } catch (Exception e) {
            throw new InternalServerException("공유 토큰 서명에 실패했습니다.", e);
        }
    }

    private String encodeUuid(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());

        return base64UrlEncoder.encodeToString(buffer.array());
    }

    private String artifactShareCode(String artifactKind, String channel) {
        if (artifactKind == null) {
            return null;
        }

        String channelSuffix = channelSuffix(channel);
        String kindCode = switch (artifactKind) {
            case "fortune" -> "F";
            case "relay_drawing" -> "R";
            case "flipbook" -> "B";
            case "infinite_canvas" -> "I";
            case "phone" -> "P";
            case "community_memo" -> "M";
            default -> artifactKind;
        };

        return "%s%s".formatted(kindCode, channelSuffix);
    }

    private String communityMemoShareCode(String channel) {
        return "%s%s".formatted(COMMUNITY_MEMO_SHARE_CODE, channelSuffix(channel));
    }

    private String channelSuffix(String channel) {
        return switch (channel) {
            case null -> "";
            case "QR_DOWNLOAD" -> "D";
            case "QR_SHARE" -> "S";
            default -> channel;
        };
    }
}
