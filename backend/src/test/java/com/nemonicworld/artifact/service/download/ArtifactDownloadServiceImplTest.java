package com.nemonicworld.artifact.service.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.artifact.repository.ArtifactImageUrlRepository;
import com.nemonicworld.artifact.repository.ArtifactImageUrlRow;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.share.config.ShareProperties;
import com.nemonicworld.share.service.SignedShareTokenIssuer;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * artifact 기반 QR 합성 공유 자산 생성/캐시 재사용 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ArtifactDownloadServiceImplTest {

    private static final String USER_UUID_VALUE = "550e8400-e29b-41d4-a716-446655440000";
    private static final UUID USER_UUID = UUID.fromString(USER_UUID_VALUE);
    private static final UUID ARTIFACT_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");

    @Mock
    private ArtifactImageUrlRepository artifactImageUrlRepository;

    @Mock
    private AnonymousUserResolver anonymousUserResolver;

    @Mock
    private ArtifactDownloadStorage artifactDownloadStorage;

    @Mock
    private ArtifactQrComposer artifactQrComposer;

    @Mock
    private SignedShareTokenIssuer signedShareTokenIssuer;

    private ArtifactQrAssetService artifactQrAssetService;

    @BeforeEach
    void setUp() {
        ArtifactDownloadTargetResolver artifactDownloadTargetResolver = new ArtifactDownloadTargetResolver();
        ArtifactQrShareUrlSupport artifactQrShareUrlSupport = new ArtifactQrShareUrlSupport(signedShareTokenIssuer,
            new ShareProperties("https://nemonic.example.com", "test-share-token-secret"));
        ArtifactQrAssetPrepareUseCase artifactQrAssetPrepareUseCase = new ArtifactQrAssetPrepareUseCase(
            artifactImageUrlRepository, anonymousUserResolver, artifactDownloadStorage, artifactQrComposer,
            artifactDownloadTargetResolver, artifactQrShareUrlSupport);
        artifactQrAssetService = new ArtifactQrAssetServiceImpl(artifactQrAssetPrepareUseCase);
    }

    /**
     * 최초 다운로드는 원본 object를 내려받아 QR 합성본을 캐시에 업로드한 뒤 attachment 파일로 반환합니다.
     */
    @Test
    void prepareDownloadFileCreatesQrComposedCacheWhenMissing() {
        byte[] sourceBytes = new byte[]{1, 2, 3};
        byte[] composedBytes = new byte[]{4, 5, 6};
        String cacheKey = "artifact-downloads/%s/result-qr-v4.jpg".formatted(ARTIFACT_ID);

        givenValidUser();
        given(artifactImageUrlRepository.findActiveArtifactImageUrl(ARTIFACT_ID, USER_UUID))
            .willReturn(Optional.of(relayRow("relay/results/a/original.png")));
        given(signedShareTokenIssuer.issueArtifactToken(ARTIFACT_ID, "relay_drawing", "QR_DOWNLOAD"))
            .willReturn("signed-share-token");
        given(artifactDownloadStorage.exists(cacheKey)).willReturn(false);
        given(artifactDownloadStorage.download("relay/results/a/original.png")).willReturn(sourceBytes);
        given(artifactQrComposer.compose("image/png", sourceBytes,
            "https://nemonic.example.com/share/signed-share-token")).willReturn(composedBytes);

        ArtifactQrAsset asset = artifactQrAssetService.prepareQrAsset(USER_UUID_VALUE, ARTIFACT_ID.toString());

        assertThat(asset.cacheObjectKey()).isEqualTo(cacheKey);
        assertThat(asset.fileName()).isEqualTo("nemonic-%s.jpg".formatted(ARTIFACT_ID));
        assertThat(asset.contentType()).isEqualTo("image/jpeg");
        assertThat(asset.shareToken()).isEqualTo("signed-share-token");
        verify(artifactDownloadStorage).upload(cacheKey, composedBytes, "image/jpeg");
    }

    /**
     * 캐시가 이미 있으면 원본 다운로드와 QR 합성을 건너뛰고 캐시 파일만 반환합니다.
     */
    @Test
    void prepareDownloadFileReusesQrComposedCacheWhenExists() {
        String cacheKey = "artifact-downloads/%s/result-qr-v4.gif".formatted(ARTIFACT_ID);

        givenValidUser();
        given(artifactImageUrlRepository.findActiveArtifactImageUrl(ARTIFACT_ID, USER_UUID))
            .willReturn(Optional.of(flipbookRow("flipbook/results/a/result.gif")));
        given(signedShareTokenIssuer.issueArtifactToken(ARTIFACT_ID, "flipbook", "QR_DOWNLOAD"))
            .willReturn("signed-flipbook-token");
        given(artifactDownloadStorage.exists(cacheKey)).willReturn(true);

        ArtifactQrAsset asset = artifactQrAssetService.prepareQrAsset(USER_UUID_VALUE, ARTIFACT_ID.toString());

        assertThat(asset.cacheObjectKey()).isEqualTo(cacheKey);
        assertThat(asset.fileName()).isEqualTo("nemonic-%s.gif".formatted(ARTIFACT_ID));
        assertThat(asset.contentType()).isEqualTo("image/gif");
        verify(artifactDownloadStorage, never()).upload(anyString(), any(), anyString());
        verify(artifactDownloadStorage, never()).download(anyString());
        verify(artifactQrComposer, never()).compose(anyString(), any(), anyString());
    }

    /**
     * 활성 gallery 소유권으로 조회되는 산출물이 없으면 404로 분류합니다.
     */
    @Test
    void prepareDownloadFileRejectsMissingArtifact() {
        givenValidUser();
        given(artifactImageUrlRepository.findActiveArtifactImageUrl(ARTIFACT_ID, USER_UUID))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> artifactQrAssetService.prepareQrAsset(USER_UUID_VALUE, ARTIFACT_ID.toString()))
            .isInstanceOf(NotFoundException.class).hasMessage("다운로드 가능한 산출물을 찾을 수 없습니다.");
    }

    /**
     * 현재 다운로드 지원 대상이 아닌 산출물 종류는 400으로 거절합니다.
     */
    @Test
    void prepareDownloadFileRejectsUnsupportedKind() {
        givenValidUser();
        ArtifactImageUrlRow unsupportedRow = new ArtifactImageUrlRow(ARTIFACT_ID, "unknown",
            "unknown/results/a/result.png", null, null, null, null, null, null, null, null);
        given(artifactImageUrlRepository.findActiveArtifactImageUrl(ARTIFACT_ID, USER_UUID))
            .willReturn(Optional.of(unsupportedRow));

        assertThatThrownBy(() -> artifactQrAssetService.prepareQrAsset(USER_UUID_VALUE, ARTIFACT_ID.toString()))
            .isInstanceOf(BadRequestException.class).hasMessage("다운로드할 수 없는 산출물 종류입니다.");
    }

    /**
     * QR 합성 다운로드는 MinIO object key 기반 산출물만 처리합니다.
     */
    @Test
    void prepareDownloadFileRejectsAbsoluteObjectReference() {
        givenValidUser();
        given(artifactImageUrlRepository.findActiveArtifactImageUrl(ARTIFACT_ID, USER_UUID))
            .willReturn(Optional.of(relayRow("https://cdn.example.com/original.png")));

        assertThatThrownBy(() -> artifactQrAssetService.prepareQrAsset(USER_UUID_VALUE, ARTIFACT_ID.toString()))
            .isInstanceOf(BadRequestException.class).hasMessage("QR 합성 다운로드는 MinIO 산출물만 지원합니다.");
    }

    /**
     * 커뮤니티 메모는 썸네일보다 최종 원본 스냅샷을 우선해 QR 합성 자산을 생성합니다.
     */
    @Test
    void prepareDownloadFileUsesCommunityMemoOriginalImageBeforeThumbnail() {
        byte[] sourceBytes = new byte[]{1, 2, 3};
        byte[] composedBytes = new byte[]{4, 5, 6};
        String cacheKey = "artifact-downloads/%s/result-qr-v4.jpg".formatted(ARTIFACT_ID);

        givenValidUser();
        given(artifactImageUrlRepository.findActiveArtifactImageUrl(ARTIFACT_ID, USER_UUID))
            .willReturn(Optional.of(communityMemoRow("community/memos/a/original.png", "community/memos/a/thumb.png")));
        given(signedShareTokenIssuer.issueArtifactToken(ARTIFACT_ID, "community_memo", "QR_DOWNLOAD"))
            .willReturn("signed-community-token");
        given(artifactDownloadStorage.exists(cacheKey)).willReturn(false);
        given(artifactDownloadStorage.download("community/memos/a/original.png")).willReturn(sourceBytes);
        given(artifactQrComposer.compose("image/png", sourceBytes,
            "https://nemonic.example.com/share/signed-community-token")).willReturn(composedBytes);

        ArtifactQrAsset asset = artifactQrAssetService.prepareQrAsset(USER_UUID_VALUE, ARTIFACT_ID.toString());

        assertThat(asset.kind()).isEqualTo("community_memo");
        verify(artifactDownloadStorage).download("community/memos/a/original.png");
        verify(artifactDownloadStorage).upload(cacheKey, composedBytes, "image/jpeg");
    }

    /**
     * 무한 캔버스 산출물은 저장된 최종 캔버스 이미지를 QR 합성 다운로드 소스로 사용합니다.
     */
    @Test
    void prepareDownloadFileUsesInfiniteCanvasImage() {
        byte[] sourceBytes = new byte[]{1, 2, 3};
        byte[] composedBytes = new byte[]{4, 5, 6};
        String cacheKey = "artifact-downloads/%s/result-qr-v4.jpg".formatted(ARTIFACT_ID);

        givenValidUser();
        given(artifactImageUrlRepository.findActiveArtifactImageUrl(ARTIFACT_ID, USER_UUID))
            .willReturn(Optional.of(infiniteCanvasRow("infinite-canvas/outputs/a/original.png")));
        given(signedShareTokenIssuer.issueArtifactToken(ARTIFACT_ID, "infinite_canvas", "QR_DOWNLOAD"))
            .willReturn("signed-canvas-token");
        given(artifactDownloadStorage.exists(cacheKey)).willReturn(false);
        given(artifactDownloadStorage.download("infinite-canvas/outputs/a/original.png")).willReturn(sourceBytes);
        given(artifactQrComposer.compose("image/png", sourceBytes,
            "https://nemonic.example.com/share/signed-canvas-token")).willReturn(composedBytes);

        ArtifactQrAsset asset = artifactQrAssetService.prepareQrAsset(USER_UUID_VALUE, ARTIFACT_ID.toString());

        assertThat(asset.kind()).isEqualTo("infinite_canvas");
        verify(artifactDownloadStorage).download("infinite-canvas/outputs/a/original.png");
        verify(artifactDownloadStorage).upload(cacheKey, composedBytes, "image/jpeg");
    }

    @Test
    void prepareDownloadFileUsesPhoneImageUrl() {
        byte[] sourceBytes = new byte[]{1, 2, 3};
        byte[] composedBytes = new byte[]{4, 5, 6};
        String cacheKey = "artifact-downloads/%s/result-qr-v4.jpg".formatted(ARTIFACT_ID);

        givenValidUser();
        given(artifactImageUrlRepository.findActiveArtifactImageUrl(ARTIFACT_ID, USER_UUID))
            .willReturn(Optional.of(phoneRow("phone/results/a/thumb.png", "phone/results/a/original.png")));
        given(signedShareTokenIssuer.issueArtifactToken(ARTIFACT_ID, "phone", "QR_DOWNLOAD"))
            .willReturn("signed-phone-token");
        given(artifactDownloadStorage.exists(cacheKey)).willReturn(false);
        given(artifactDownloadStorage.download("phone/results/a/original.png")).willReturn(sourceBytes);
        given(artifactQrComposer.compose("image/png", sourceBytes,
            "https://nemonic.example.com/share/signed-phone-token")).willReturn(composedBytes);

        ArtifactQrAsset asset = artifactQrAssetService.prepareQrAsset(USER_UUID_VALUE, ARTIFACT_ID.toString());

        assertThat(asset.kind()).isEqualTo("phone");
        assertThat(asset.cacheObjectKey()).isEqualTo(cacheKey);
        assertThat(asset.fileName()).isEqualTo("nemonic-%s.jpg".formatted(ARTIFACT_ID));
        assertThat(asset.contentType()).isEqualTo("image/jpeg");
        verify(artifactDownloadStorage).download("phone/results/a/original.png");
        verify(artifactDownloadStorage).upload(cacheKey, composedBytes, "image/jpeg");
    }

    @Test
    void prepareDownloadFileFallsBackToPhoneThumbnailWhenImageMissing() {
        byte[] sourceBytes = new byte[]{1, 2, 3};
        byte[] composedBytes = new byte[]{4, 5, 6};
        String cacheKey = "artifact-downloads/%s/result-qr-v4.jpg".formatted(ARTIFACT_ID);

        givenValidUser();
        given(artifactImageUrlRepository.findActiveArtifactImageUrl(ARTIFACT_ID, USER_UUID))
            .willReturn(Optional.of(phoneRow("phone/results/a/thumb.png", null)));
        given(signedShareTokenIssuer.issueArtifactToken(ARTIFACT_ID, "phone", "QR_DOWNLOAD"))
            .willReturn("signed-phone-token");
        given(artifactDownloadStorage.exists(cacheKey)).willReturn(false);
        given(artifactDownloadStorage.download("phone/results/a/thumb.png")).willReturn(sourceBytes);
        given(artifactQrComposer.compose("image/png", sourceBytes,
            "https://nemonic.example.com/share/signed-phone-token")).willReturn(composedBytes);

        ArtifactQrAsset asset = artifactQrAssetService.prepareQrAsset(USER_UUID_VALUE, ARTIFACT_ID.toString());

        assertThat(asset.kind()).isEqualTo("phone");
        verify(artifactDownloadStorage).download("phone/results/a/thumb.png");
        verify(artifactDownloadStorage).upload(cacheKey, composedBytes, "image/jpeg");
    }

    @Test
    void prepareDownloadFileRejectsAbsolutePhoneImageReference() {
        givenValidUser();
        given(artifactImageUrlRepository.findActiveArtifactImageUrl(ARTIFACT_ID, USER_UUID))
            .willReturn(Optional.of(phoneRow("phone/results/a/thumb.png", "https://cdn.example.com/phone.png")));

        assertThatThrownBy(() -> artifactQrAssetService.prepareQrAsset(USER_UUID_VALUE, ARTIFACT_ID.toString()))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("MinIO");
    }

    private void givenValidUser() {
        given(anonymousUserResolver.parseUuid(USER_UUID_VALUE)).willReturn(USER_UUID);
    }

    private ArtifactImageUrlRow relayRow(String combinedPreviewUrl) {
        return new ArtifactImageUrlRow(ARTIFACT_ID, "relay_drawing", "relay/results/a/thumb.png", null,
            combinedPreviewUrl, null, null, null, null, null, null);
    }

    private ArtifactImageUrlRow flipbookRow(String gifUrl) {
        return new ArtifactImageUrlRow(ARTIFACT_ID, "flipbook", "flipbook/results/a/thumb.png", null, null, gifUrl,
            "flipbook/results/a/first.png", null, null, null, null);
    }

    private ArtifactImageUrlRow communityMemoRow(String originalImageUrl, String thumbnailImageUrl) {
        return new ArtifactImageUrlRow(ARTIFACT_ID, "community_memo", "community/memos/a/artifact-thumb.png", null,
            null, null, null, null, null, originalImageUrl, thumbnailImageUrl);
    }

    private ArtifactImageUrlRow infiniteCanvasRow(String canvasImageUrl) {
        return new ArtifactImageUrlRow(ARTIFACT_ID, "infinite_canvas", "infinite-canvas/outputs/a/thumb.png", null,
            null, null, null, canvasImageUrl, null, null, null);
    }

    private ArtifactImageUrlRow phoneRow(String thumbnailUrl, String phoneImageUrl) {
        return new ArtifactImageUrlRow(ARTIFACT_ID, "phone", thumbnailUrl, null, null, null, null, null, phoneImageUrl,
            null, null);
    }
}
