package com.nemonicworld.artifact.service.share;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.nemonicworld.artifact.service.download.ArtifactQrAsset;
import com.nemonicworld.artifact.service.download.ArtifactQrAssetService;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.share.config.ShareProperties;
import com.nemonicworld.share.dto.response.ShareCreateResponse;
import com.nemonicworld.share.service.ShareEventLogger;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArtifactShareServiceImplTest {

    private static final String USER_UUID_VALUE = "550e8400-e29b-41d4-a716-446655440000";
    private static final UUID ARTIFACT_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");
    private static final String QR_IMAGE_URL = "https://minio.example.com/nemonic/artifact-downloads/result-qr-v2.gif";
    private static final String KAKAO_URL = "https://nemonic.example.com?utm_source=kakao&utm_medium=social"
        + "&utm_campaign=flipbook_result&share_token=signed-share-token";
    private static final String INSTAGRAM_URL = "https://nemonic.example.com?utm_source=instagram&utm_medium=story"
        + "&utm_campaign=flipbook_result&share_token=signed-share-token";

    @Mock
    private ArtifactQrAssetService artifactQrAssetService;

    @Mock
    private MinioPublicUrlResolver minioPublicUrlResolver;

    @Mock
    private ShareEventLogger shareEventLogger;

    private ArtifactShareServiceImpl artifactShareService;

    @BeforeEach
    void setUp() {
        artifactShareService = new ArtifactShareServiceImpl(artifactQrAssetService, minioPublicUrlResolver,
            new ShareProperties("https://nemonic.example.com/", "test-share-token-secret"), shareEventLogger);
    }

    /**
     * artifact 공유 API는 QR 합성 캐시 URL과 플랫폼별 UTM URL을 반환합니다.
     */
    @Test
    void createArtifactShareReturnsQrImageUrlAndPlatformUrls() {
        ArtifactQrAsset asset = new ArtifactQrAsset(ARTIFACT_ID, "flipbook",
            "artifact-downloads/%s/result-qr-v2.gif".formatted(ARTIFACT_ID), "nemonic-%s.gif".formatted(ARTIFACT_ID),
            "image/gif", "signed-share-token");
        given(artifactQrAssetService.prepareQrAsset(USER_UUID_VALUE, ARTIFACT_ID.toString())).willReturn(asset);
        given(minioPublicUrlResolver.resolve(asset.cacheObjectKey())).willReturn(QR_IMAGE_URL);

        ShareCreateResponse response = artifactShareService.createArtifactShare(USER_UUID_VALUE,
            ARTIFACT_ID.toString());

        assertThat(response.shareToken()).isEqualTo("signed-share-token");
        assertThat(response.imageUrl()).isEqualTo(QR_IMAGE_URL);
        assertThat(response.siteUrl()).isEqualTo("https://nemonic.example.com");
        assertThat(response.kakaoUrl()).isEqualTo(KAKAO_URL);
        assertThat(response.instagramUrl()).isEqualTo(INSTAGRAM_URL);
        verify(shareEventLogger).logArtifactShareCreated(USER_UUID_VALUE, "signed-share-token", ARTIFACT_ID, "flipbook",
            "flipbook_result");
    }
}
