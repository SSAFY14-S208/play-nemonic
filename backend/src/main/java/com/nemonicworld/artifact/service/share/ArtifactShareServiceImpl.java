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
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ArtifactShareServiceImpl implements ArtifactShareService {

    private static final String SHARE_IMAGE_REQUIRED_MESSAGE = "공유할 이미지 URL이 없습니다.";
    private static final String KAKAO_SOURCE = "kakao";
    private static final String INSTAGRAM_SOURCE = "instagram";
    private static final String KAKAO_MEDIUM = "social";
    private static final String INSTAGRAM_MEDIUM = "story";

    private final ArtifactQrAssetService artifactQrAssetService;
    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final ShareProperties shareProperties;
    private final ShareEventLogger shareEventLogger;

    public ArtifactShareServiceImpl(ArtifactQrAssetService artifactQrAssetService,
        MinioPublicUrlResolver minioPublicUrlResolver, ShareProperties shareProperties,
        ShareEventLogger shareEventLogger) {
        this.artifactQrAssetService = artifactQrAssetService;
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.shareProperties = shareProperties;
        this.shareEventLogger = shareEventLogger;
    }

    @Transactional
    @Override
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
        String siteUrl = normalizeSiteUrl(shareProperties.siteUrl());
        String kakaoUrl = createPlatformUrl(siteUrl, KAKAO_SOURCE, KAKAO_MEDIUM, campaign, asset.shareToken());
        String instagramUrl = createPlatformUrl(siteUrl, INSTAGRAM_SOURCE, INSTAGRAM_MEDIUM, campaign,
            asset.shareToken());

        shareEventLogger.logArtifactShareCreated(userUuidValue, asset.shareToken(), asset.artifactId(), asset.kind(),
            campaign);

        return new ShareCreateResponse(asset.shareToken(), imageUrl, siteUrl, kakaoUrl, instagramUrl);
    }

    private String normalizeSiteUrl(String siteUrl) {
        return siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    private String createPlatformUrl(String siteUrl, String source, String medium, String campaign, String shareToken) {
        return UriComponentsBuilder.fromUriString(siteUrl).queryParam("utm_source", source)
            .queryParam("utm_medium", medium).queryParam("utm_campaign", campaign).queryParam("share_token", shareToken)
            .build().toUriString();
    }
}
