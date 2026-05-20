package com.nemonicworld.share.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.share.config.ShareProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * DB 없이 검증 가능한 공유 토큰 payload 발급을 검증합니다.
 */
class SignedShareTokenIssuerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SignedShareTokenIssuer issuer = new SignedShareTokenIssuer(objectMapper,
        new ShareProperties("https://nemonic.example.com", "test-share-token-secret"));

    /**
     * 같은 산출물/채널은 사용자와 관계없이 캐시 재사용이 가능하도록 같은 signed token을 발급합니다.
     */
    @Test
    void issueArtifactTokenReturnsDeterministicPayloadToken() throws Exception {
        UUID artifactId = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

        String token = issuer.issueArtifactToken(artifactId, "flipbook", "QR_DOWNLOAD");

        assertThat(token).isEqualTo(issuer.issueArtifactToken(artifactId, "flipbook", "QR_DOWNLOAD"));
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).startsWith("1BD");
        assertThat(parts[1]).hasSize(8);
        assertThat(token).hasSizeLessThan(40);
    }

    @Test
    void issueCommunityMemoTokenReturnsDeterministicPayloadToken() throws Exception {
        UUID memoId = UUID.fromString("770e8400-e29b-41d4-a716-446655440000");

        String token = issuer.issueCommunityMemoToken(memoId, "QR_SHARE");

        assertThat(token).isEqualTo(issuer.issueCommunityMemoToken(memoId, "QR_SHARE"));
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).startsWith("1MS");
        assertThat(parts[1]).hasSize(8);
        assertThat(token).hasSizeLessThan(40);
    }
}
