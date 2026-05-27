package com.nemonicworld.artifact.service.share;

import com.nemonicworld.artifact.service.download.ArtifactQrAsset;
import com.nemonicworld.artifact.service.download.ArtifactQrAssetService;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import com.nemonicworld.share.config.ShareProperties;
import com.nemonicworld.share.dto.response.ShareCreateResponse;
import com.nemonicworld.share.service.ShareEventLogger;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ArtifactShareCreateUseCase {

    private static final String SHARE_IMAGE_REQUIRED_MESSAGE = "공유할 이미지 URL이 없습니다.";

    private final ArtifactQrAssetService artifactQrAssetService;
    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final ShareProperties shareProperties;
    private final ShareEventLogger shareEventLogger;
    private final ArtifactShareUrlSupport artifactShareUrlSupport;

    public ArtifactShareCreateUseCase(ArtifactQrAssetService artifactQrAssetService,
        MinioPublicUrlResolver minioPublicUrlResolver, ShareProperties shareProperties,
        ShareEventLogger shareEventLogger, ArtifactShareUrlSupport artifactShareUrlSupport) {
        this.artifactQrAssetService = artifactQrAssetService;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.shareProperties = shareProperties;
        this.shareEventLogger = shareEventLogger;
        this.artifactShareUrlSupport = artifactShareUrlSupport;
    }

    @Transactional
    public ShareCreateResponse createArtifactShare(String userUuidValue, String artifactIdValue) {
        try {
            return createArtifactShareInternal(userUuidValue, artifactIdValue);
        } catch (RuntimeException e) {
            shareEventLogger.logArtifactShareCreateFailed(userUuidValue, artifactIdValue, e);
            throw e;
        }
    }

    private ShareCreateResponse createArtifactShareInternal(String userUuidValue, String artifactIdValue) {
        ArtifactQrAsset asset = artifactQrAssetService.prepareQrAsset(userUuidValue, artifactIdValue);
        String imageUrl = minioPublicUrlResolver.resolve(asset.cacheObjectKey());
        if (!StringUtils.hasText(imageUrl)) {
            throw new BadRequestException(SHARE_IMAGE_REQUIRED_MESSAGE);
        }

        String campaign = "%s_result".formatted(asset.kind().toLowerCase(Locale.ROOT));
        String siteUrl = artifactShareUrlSupport.normalizeSiteUrl(shareProperties.siteUrl());
        String kakaoUrl = artifactShareUrlSupport.createKakaoUrl(siteUrl, campaign, asset.shareToken());
        String instagramUrl = artifactShareUrlSupport.createInstagramUrl(siteUrl, campaign, asset.shareToken());

        shareEventLogger.logArtifactShareCreated(userUuidValue, asset.shareToken(), asset.artifactId(), asset.kind(),
            campaign);

        return new ShareCreateResponse(asset.shareToken(), imageUrl, siteUrl, kakaoUrl, instagramUrl);
    }
}
