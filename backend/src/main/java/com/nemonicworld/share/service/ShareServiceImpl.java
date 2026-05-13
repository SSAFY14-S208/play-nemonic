package com.nemonicworld.share.service;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.gallery.dto.response.GalleryDetailResponse;
import com.nemonicworld.gallery.service.GalleryService;
import com.nemonicworld.share.config.ShareProperties;
import com.nemonicworld.share.dto.request.ShareCreateRequest;
import com.nemonicworld.share.dto.response.ShareCreateResponse;
import com.nemonicworld.share.util.ShareTokenGenerator;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private static final String SHARE_IMAGE_REQUIRED_MESSAGE = "공유할 이미지 URL이 없습니다.";
    private static final String KAKAO_SOURCE = "kakao";
    private static final String INSTAGRAM_SOURCE = "instagram";
    private static final String KAKAO_MEDIUM = "social";
    private static final String INSTAGRAM_MEDIUM = "story";

    private final GalleryService galleryService;
    private final ShareProperties shareProperties;
    private final ShareTokenGenerator shareTokenGenerator;
    private final ShareEventLogger shareEventLogger;

    /**
     * 소유 갤러리 항목을 확인하고 SNS 공유에 필요한 이미지 URL과 플랫폼별 UTM URL을 생성합니다.
     */
    @Transactional(readOnly = true)
    @Override
    public ShareCreateResponse createShare(String userUuidValue, ShareCreateRequest request) {
        try {
            return createShareInternal(userUuidValue, request);
        } catch (RuntimeException e) {
            shareEventLogger.logShareCreateFailed(userUuidValue, request == null ? null : request.galleryId(), e);
            throw e;
        }
    }

    private ShareCreateResponse createShareInternal(String userUuidValue, ShareCreateRequest request) {
        GalleryDetailResponse galleryItem = galleryService.getMyGalleryItemDetail(userUuidValue, request.galleryId());
        String imageUrl = selectImageUrl(galleryItem);
        String campaign = resolveCampaign(request.campaign(), galleryItem.kind());
        String shareToken = shareTokenGenerator.generate();
        String siteUrl = normalizeSiteUrl(shareProperties.siteUrl());
        String kakaoUrl = createPlatformUrl(siteUrl, KAKAO_SOURCE, KAKAO_MEDIUM, campaign, shareToken);
        String instagramUrl = createPlatformUrl(siteUrl, INSTAGRAM_SOURCE, INSTAGRAM_MEDIUM, campaign, shareToken);

        shareEventLogger.logShareCreated(userUuidValue, shareToken, galleryItem, campaign);

        return new ShareCreateResponse(shareToken, imageUrl, siteUrl, kakaoUrl, instagramUrl);
    }

    private String selectImageUrl(GalleryDetailResponse galleryItem) {
        if (StringUtils.hasText(galleryItem.contentUrl())) {
            return galleryItem.contentUrl();
        }

        if (StringUtils.hasText(galleryItem.thumbnailUrl())) {
            return galleryItem.thumbnailUrl();
        }

        throw new BadRequestException(SHARE_IMAGE_REQUIRED_MESSAGE);
    }

    private String resolveCampaign(String campaign, String artifactKind) {
        if (StringUtils.hasText(campaign)) {
            return campaign.trim();
        }

        return artifactKind.toLowerCase(Locale.ROOT) + "_result";
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
