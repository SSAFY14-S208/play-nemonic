package com.nemonicworld.community.service.memo;

import com.nemonicworld.artifact.service.download.ArtifactDownloadStorage;
import com.nemonicworld.artifact.service.download.ArtifactQrComposer;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.community.entity.CommunityMemoModerationStatus;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.share.config.ShareProperties;
import com.nemonicworld.share.dto.response.ShareCreateResponse;
import com.nemonicworld.share.service.ShareEventLogger;
import com.nemonicworld.share.service.SignedShareTokenIssuer;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
class CommunityMemoShareUseCase {

    private static final String COMMUNITY_MEMO_SHARE_IMAGE_REQUIRED_MESSAGE = "커뮤니티 메모 공유 이미지가 없습니다.";
    private static final String COMMUNITY_MEMO_SHARE_BLOCKED_MESSAGE = "공유할 수 없는 커뮤니티 메모입니다.";
    private static final String EXTERNAL_OBJECT_REFERENCE_MESSAGE = "QR 합성 공유는 MinIO 오브젝트 키 기반 이미지만 지원합니다.";
    private static final String COMMUNITY_MEMO_SHARE_NOT_FOUND_EVENT = "community_memo_share_not_found";
    private static final String COMMUNITY_MEMO_RESULT_CAMPAIGN = "community_memo_result";
    private static final String CHANNEL_QR_SHARE = "QR_SHARE";
    private static final String KAKAO_SOURCE = "kakao";
    private static final String INSTAGRAM_SOURCE = "instagram";
    private static final String KAKAO_MEDIUM = "social";
    private static final String INSTAGRAM_MEDIUM = "story";
    private static final String STATIC_IMAGE_CONTENT_TYPE = "image/png";
    private static final String STATIC_SHARE_CONTENT_TYPE = "image/jpeg";

    private final CommunityMemoSupport communityMemoSupport;
    private final ArtifactDownloadStorage artifactDownloadStorage;
    private final ArtifactQrComposer artifactQrComposer;
    private final SignedShareTokenIssuer signedShareTokenIssuer;
    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final ShareProperties shareProperties;
    private final ShareEventLogger shareEventLogger;

    CommunityMemoShareUseCase(CommunityMemoSupport communityMemoSupport,
        ArtifactDownloadStorage artifactDownloadStorage, ArtifactQrComposer artifactQrComposer,
        SignedShareTokenIssuer signedShareTokenIssuer, MinioPublicUrlResolver minioPublicUrlResolver,
        ShareProperties shareProperties, ShareEventLogger shareEventLogger) {
        this.communityMemoSupport = communityMemoSupport;
        this.artifactDownloadStorage = artifactDownloadStorage;
        this.artifactQrComposer = artifactQrComposer;
        this.signedShareTokenIssuer = signedShareTokenIssuer;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.shareProperties = shareProperties;
        this.shareEventLogger = shareEventLogger;
    }

    @Transactional
    ShareCreateResponse createCommunityMemoShare(String memoIdValue, String userUuidValue) {
        try {
            return createCommunityMemoShareInternal(memoIdValue, userUuidValue);
        } catch (RuntimeException e) {
            shareEventLogger.logCommunityMemoShareCreateFailed(userUuidValue, memoIdValue, e);
            throw e;
        }
    }

    private ShareCreateResponse createCommunityMemoShareInternal(String memoIdValue, String userUuidValue) {
        UUID memoId = communityMemoSupport.parseUserUuid(memoIdValue);
        UUID userUuid = communityMemoSupport.parseUserUuid(userUuidValue);
        communityMemoSupport.resolveUser(userUuid);

        CommunityMemoDetailRow row = communityMemoSupport.findVisibleMemoOrLogNotFound(memoId, userUuid,
            COMMUNITY_MEMO_SHARE_NOT_FOUND_EVENT);
        validateShareable(row);

        String objectKey = selectSourceObjectKey(row).trim();
        String shareToken = signedShareTokenIssuer.issueCommunityMemoToken(memoId, CHANNEL_QR_SHARE);
        String cacheObjectKey = cacheObjectKey(memoId);
        if (!artifactDownloadStorage.exists(cacheObjectKey)) {
            byte[] sourceBytes = artifactDownloadStorage.download(objectKey);
            byte[] composedBytes = artifactQrComposer.compose(STATIC_IMAGE_CONTENT_TYPE, sourceBytes,
                qrUrl(shareToken));
            artifactDownloadStorage.upload(cacheObjectKey, composedBytes, STATIC_SHARE_CONTENT_TYPE);
        }

        String imageUrl = minioPublicUrlResolver.resolve(cacheObjectKey);
        if (!StringUtils.hasText(imageUrl)) {
            throw new BadRequestException(COMMUNITY_MEMO_SHARE_IMAGE_REQUIRED_MESSAGE);
        }

        String siteUrl = normalizeSiteUrl(shareProperties.siteUrl());
        String kakaoUrl = createPlatformUrl(siteUrl, KAKAO_SOURCE, KAKAO_MEDIUM, shareToken);
        String instagramUrl = createPlatformUrl(siteUrl, INSTAGRAM_SOURCE, INSTAGRAM_MEDIUM, shareToken);
        shareEventLogger.logCommunityMemoShareCreated(userUuidValue, shareToken, memoId,
            COMMUNITY_MEMO_RESULT_CAMPAIGN);

        return new ShareCreateResponse(shareToken, imageUrl, siteUrl, kakaoUrl, instagramUrl);
    }

    private void validateShareable(CommunityMemoDetailRow row) {
        if (CommunityMemoModerationStatus.BLOCKED.value().equals(row.moderationStatus())) {
            throw new BadRequestException(COMMUNITY_MEMO_SHARE_BLOCKED_MESSAGE);
        }
    }

    private String selectSourceObjectKey(CommunityMemoDetailRow row) {
        String objectKey = firstText(row.originalImageReference(), row.thumbnailImageReference());
        if (!StringUtils.hasText(objectKey)) {
            throw new BadRequestException(COMMUNITY_MEMO_SHARE_IMAGE_REQUIRED_MESSAGE);
        }
        if (isAbsoluteUrl(objectKey.trim())) {
            throw new BadRequestException(EXTERNAL_OBJECT_REFERENCE_MESSAGE);
        }

        return objectKey;
    }

    private String qrUrl(String shareToken) {
        return UriComponentsBuilder.fromUriString(normalizeSiteUrl(shareProperties.siteUrl()))
            .path("/share/{shareToken}").build(shareToken).toString();
    }

    private String cacheObjectKey(UUID memoId) {
        return "community-memo-shares/%s/result-qr.jpg".formatted(memoId);
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String normalizeSiteUrl(String siteUrl) {
        return siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    private String createPlatformUrl(String siteUrl, String source, String medium, String shareToken) {
        return UriComponentsBuilder.fromUriString(siteUrl).queryParam("utm_source", source)
            .queryParam("utm_medium", medium).queryParam("utm_campaign", COMMUNITY_MEMO_RESULT_CAMPAIGN)
            .queryParam("share_token", shareToken).build().toUriString();
    }

    private boolean isAbsoluteUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
