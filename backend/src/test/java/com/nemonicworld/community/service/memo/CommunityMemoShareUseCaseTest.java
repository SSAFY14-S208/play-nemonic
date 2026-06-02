package com.nemonicworld.community.service.memo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nemonicworld.artifact.service.download.ArtifactDownloadStorage;
import com.nemonicworld.artifact.service.download.ArtifactQrComposer;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.share.config.ShareProperties;
import com.nemonicworld.share.dto.response.ShareCreateResponse;
import com.nemonicworld.share.service.ShareEventLogger;
import com.nemonicworld.share.service.SignedShareTokenIssuer;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommunityMemoShareUseCaseTest {

    private static final String USER_UUID_VALUE = "550e8400-e29b-41d4-a716-446655440000";
    private static final UUID USER_UUID = UUID.fromString(USER_UUID_VALUE);
    private static final UUID OWNER_UUID = UUID.fromString("111e8400-e29b-41d4-a716-446655440000");
    private static final UUID MEMO_ID = UUID.fromString("660e8400-e29b-41d4-a716-446655440000");
    private static final String ORIGINAL_OBJECT_KEY = "community/memos/a/original.png";
    private static final String THUMBNAIL_OBJECT_KEY = "community/memos/a/thumb.png";
    private static final String GIF_OBJECT_KEY = "flipbook/results/a/result.gif";
    private static final String CACHE_OBJECT_KEY = "community-memo-shares/%s/result-qr-v3.jpg".formatted(MEMO_ID);
    private static final String GIF_CACHE_OBJECT_KEY = "community-memo-shares/%s/result-qr-v3.gif".formatted(MEMO_ID);
    private static final String SHARE_TOKEN = "signed-community-token";
    private static final String QR_IMAGE_URL = "https://minio.example.com/nemonic/community-memo-shares/"
        + "660e8400-e29b-41d4-a716-446655440000/result-qr-v3.jpg";
    private static final String QR_GIF_URL = "https://minio.example.com/nemonic/community-memo-shares/"
        + "660e8400-e29b-41d4-a716-446655440000/result-qr-v3.gif";
    private static final String KAKAO_URL = "https://nemonic.example.com?utm_source=kakao&utm_medium=social"
        + "&utm_campaign=community_memo_result&share_token=signed-community-token";
    private static final String INSTAGRAM_URL = "https://nemonic.example.com?utm_source=instagram&utm_medium=story"
        + "&utm_campaign=community_memo_result&share_token=signed-community-token";

    @Mock
    private CommunityMemoSupport communityMemoSupport;

    @Mock
    private ArtifactDownloadStorage artifactDownloadStorage;

    @Mock
    private ArtifactQrComposer artifactQrComposer;

    @Mock
    private SignedShareTokenIssuer signedShareTokenIssuer;

    @Mock
    private MinioPublicUrlResolver minioPublicUrlResolver;

    @Mock
    private ShareEventLogger shareEventLogger;

    private CommunityMemoShareUseCase communityMemoShareUseCase;

    @BeforeEach
    void setUp() {
        CommunityMemoShareQrCacheSupport qrCacheSupport = new CommunityMemoShareQrCacheSupport(artifactDownloadStorage,
            artifactQrComposer);
        CommunityMemoShareUrlSupport urlSupport = new CommunityMemoShareUrlSupport(signedShareTokenIssuer,
            new ShareProperties("https://nemonic.example.com/", "test-share-token-secret"));
        communityMemoShareUseCase = new CommunityMemoShareUseCase(communityMemoSupport,
            new CommunityMemoShareSourceResolver(), qrCacheSupport, urlSupport, minioPublicUrlResolver,
            shareEventLogger);
    }

    @Test
    void createCommunityMemoShareCreatesQrImageFromOriginalImage() {
        byte[] sourceBytes = new byte[]{1, 2, 3};
        byte[] composedBytes = new byte[]{4, 5, 6};
        givenVisibleMemo(visibleRow(ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, "allowed"));
        given(signedShareTokenIssuer.issueCommunityMemoToken(MEMO_ID, "QR_SHARE")).willReturn(SHARE_TOKEN);
        given(artifactDownloadStorage.exists(CACHE_OBJECT_KEY)).willReturn(false);
        given(artifactDownloadStorage.download(ORIGINAL_OBJECT_KEY)).willReturn(sourceBytes);
        given(artifactQrComposer.compose("image/png", sourceBytes,
            "https://nemonic.example.com/share/signed-community-token")).willReturn(composedBytes);
        given(minioPublicUrlResolver.resolve(CACHE_OBJECT_KEY)).willReturn(QR_IMAGE_URL);

        ShareCreateResponse response = communityMemoShareUseCase.createCommunityMemoShare(MEMO_ID.toString(),
            USER_UUID_VALUE);

        assertThat(response.shareToken()).isEqualTo(SHARE_TOKEN);
        assertThat(response.imageUrl()).isEqualTo(QR_IMAGE_URL);
        assertThat(response.siteUrl()).isEqualTo("https://nemonic.example.com");
        assertThat(response.kakaoUrl()).isEqualTo(KAKAO_URL);
        assertThat(response.instagramUrl()).isEqualTo(INSTAGRAM_URL);
        verify(artifactDownloadStorage).download(ORIGINAL_OBJECT_KEY);
        verify(artifactDownloadStorage).upload(CACHE_OBJECT_KEY, composedBytes, "image/jpeg");
        verify(shareEventLogger).logCommunityMemoShareCreated(USER_UUID_VALUE, SHARE_TOKEN, MEMO_ID,
            "community_memo_result");
    }

    @Test
    void createCommunityMemoShareFallsBackToThumbnailImage() {
        byte[] sourceBytes = new byte[]{1, 2, 3};
        byte[] composedBytes = new byte[]{4, 5, 6};
        givenVisibleMemo(visibleRow(null, THUMBNAIL_OBJECT_KEY, "allowed"));
        given(signedShareTokenIssuer.issueCommunityMemoToken(MEMO_ID, "QR_SHARE")).willReturn(SHARE_TOKEN);
        given(artifactDownloadStorage.exists(CACHE_OBJECT_KEY)).willReturn(false);
        given(artifactDownloadStorage.download(THUMBNAIL_OBJECT_KEY)).willReturn(sourceBytes);
        given(artifactQrComposer.compose("image/png", sourceBytes,
            "https://nemonic.example.com/share/signed-community-token")).willReturn(composedBytes);
        given(minioPublicUrlResolver.resolve(CACHE_OBJECT_KEY)).willReturn(QR_IMAGE_URL);

        communityMemoShareUseCase.createCommunityMemoShare(MEMO_ID.toString(), USER_UUID_VALUE);

        verify(artifactDownloadStorage).download(THUMBNAIL_OBJECT_KEY);
    }

    @Test
    void createCommunityMemoShareCreatesQrGifFromFlipbookPlaybackImage() {
        byte[] sourceBytes = new byte[]{1, 2, 3};
        byte[] composedBytes = new byte[]{4, 5, 6};
        givenVisibleMemo(visibleRow(ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, GIF_OBJECT_KEY, "flipbook", "allowed"));
        given(signedShareTokenIssuer.issueCommunityMemoToken(MEMO_ID, "QR_SHARE")).willReturn(SHARE_TOKEN);
        given(artifactDownloadStorage.exists(GIF_CACHE_OBJECT_KEY)).willReturn(false);
        given(artifactDownloadStorage.download(GIF_OBJECT_KEY)).willReturn(sourceBytes);
        given(artifactQrComposer.compose("image/gif", sourceBytes,
            "https://nemonic.example.com/share/signed-community-token")).willReturn(composedBytes);
        given(minioPublicUrlResolver.resolve(GIF_CACHE_OBJECT_KEY)).willReturn(QR_GIF_URL);

        ShareCreateResponse response = communityMemoShareUseCase.createCommunityMemoShare(MEMO_ID.toString(),
            USER_UUID_VALUE);

        assertThat(response.imageUrl()).isEqualTo(QR_GIF_URL);
        verify(artifactDownloadStorage).download(GIF_OBJECT_KEY);
        verify(artifactDownloadStorage).upload(GIF_CACHE_OBJECT_KEY, composedBytes, "image/gif");
        verify(artifactQrComposer).compose("image/gif", sourceBytes,
            "https://nemonic.example.com/share/signed-community-token");
    }

    @Test
    void createCommunityMemoShareReusesCachedQrImage() {
        givenVisibleMemo(visibleRow(ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, "allowed"));
        given(signedShareTokenIssuer.issueCommunityMemoToken(MEMO_ID, "QR_SHARE")).willReturn(SHARE_TOKEN);
        given(artifactDownloadStorage.exists(CACHE_OBJECT_KEY)).willReturn(true);
        given(minioPublicUrlResolver.resolve(CACHE_OBJECT_KEY)).willReturn(QR_IMAGE_URL);

        communityMemoShareUseCase.createCommunityMemoShare(MEMO_ID.toString(), USER_UUID_VALUE);

        verify(artifactDownloadStorage, never()).download(anyString());
        verify(artifactDownloadStorage, never()).upload(anyString(), any(), anyString());
        verify(artifactQrComposer, never()).compose(anyString(), any(), anyString());
    }

    @Test
    void createCommunityMemoShareRejectsBlockedMemo() {
        givenVisibleMemo(visibleRow(ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, "blocked"));

        assertThatThrownBy(
            () -> communityMemoShareUseCase.createCommunityMemoShare(MEMO_ID.toString(), USER_UUID_VALUE))
            .isInstanceOf(BadRequestException.class);

        verify(artifactDownloadStorage, never()).download(anyString());
        verify(shareEventLogger).logCommunityMemoShareCreateFailed(anyString(), anyString(), any());
    }

    @Test
    void createCommunityMemoShareRejectsAbsoluteSourceImage() {
        givenVisibleMemo(visibleRow("https://cdn.example.com/original.png", THUMBNAIL_OBJECT_KEY, "allowed"));

        assertThatThrownBy(
            () -> communityMemoShareUseCase.createCommunityMemoShare(MEMO_ID.toString(), USER_UUID_VALUE))
            .isInstanceOf(BadRequestException.class);

        verify(artifactDownloadStorage, never()).download(anyString());
    }

    @Test
    void createCommunityMemoShareRejectsAbsolutePlaybackImage() {
        givenVisibleMemo(visibleRow(ORIGINAL_OBJECT_KEY, THUMBNAIL_OBJECT_KEY, "https://cdn.example.com/result.gif",
            "flipbook", "allowed"));

        assertThatThrownBy(
            () -> communityMemoShareUseCase.createCommunityMemoShare(MEMO_ID.toString(), USER_UUID_VALUE))
            .isInstanceOf(BadRequestException.class);

        verify(artifactDownloadStorage, never()).download(anyString());
    }

    @Test
    void createCommunityMemoSharePropagatesNotFoundForUnavailableMemo() {
        givenParsedIds();
        given(communityMemoSupport.findVisibleMemoOrLogNotFound(MEMO_ID, USER_UUID, "community_memo_share_not_found"))
            .willThrow(new NotFoundException("not found"));

        assertThatThrownBy(
            () -> communityMemoShareUseCase.createCommunityMemoShare(MEMO_ID.toString(), USER_UUID_VALUE))
            .isInstanceOf(NotFoundException.class);

        verify(artifactDownloadStorage, never()).download(anyString());
    }

    private void givenVisibleMemo(CommunityMemoDetailRow row) {
        givenParsedIds();
        given(communityMemoSupport.findVisibleMemoOrLogNotFound(MEMO_ID, USER_UUID, "community_memo_share_not_found"))
            .willReturn(row);
    }

    private void givenParsedIds() {
        given(communityMemoSupport.parseUserUuid(MEMO_ID.toString())).willReturn(MEMO_ID);
        given(communityMemoSupport.parseUserUuid(USER_UUID_VALUE)).willReturn(USER_UUID);
    }

    private CommunityMemoDetailRow visibleRow(String originalImageReference, String thumbnailImageReference,
        String moderationStatus) {
        return visibleRow(originalImageReference, thumbnailImageReference, null, null, moderationStatus);
    }

    private CommunityMemoDetailRow visibleRow(String originalImageReference, String thumbnailImageReference,
        String playbackImageReference, String artifactKind, String moderationStatus) {
        LocalDateTime now = LocalDateTime.now();

        return new CommunityMemoDetailRow(MEMO_ID, OWNER_UUID, "owner", null, artifactKind, originalImageReference,
            thumbnailImageReference, playbackImageReference, 0.0, 0.0, 0, 0.0F, "{}", 0, moderationStatus, now, now,
            now);
    }
}
