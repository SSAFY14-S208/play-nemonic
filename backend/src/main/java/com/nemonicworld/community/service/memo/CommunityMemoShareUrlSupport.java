package com.nemonicworld.community.service.memo;

import com.nemonicworld.share.config.ShareProperties;
import com.nemonicworld.share.service.SignedShareTokenIssuer;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
class CommunityMemoShareUrlSupport {

    static final String COMMUNITY_MEMO_RESULT_CAMPAIGN = "community_memo_result";

    private static final String CHANNEL_QR_SHARE = "QR_SHARE";
    private static final String KAKAO_SOURCE = "kakao";
    private static final String INSTAGRAM_SOURCE = "instagram";
    private static final String KAKAO_MEDIUM = "social";
    private static final String INSTAGRAM_MEDIUM = "story";

    private final SignedShareTokenIssuer signedShareTokenIssuer;
    private final ShareProperties shareProperties;

    CommunityMemoShareUrlSupport(SignedShareTokenIssuer signedShareTokenIssuer, ShareProperties shareProperties) {
        this.signedShareTokenIssuer = signedShareTokenIssuer;
        this.shareProperties = shareProperties;
    }

    ShareUrls createShareUrls(UUID memoId) {
        String shareToken = signedShareTokenIssuer.issueCommunityMemoToken(memoId, CHANNEL_QR_SHARE);
        String siteUrl = normalizeSiteUrl(shareProperties.siteUrl());
        String qrUrl = qrUrl(siteUrl, shareToken);
        String kakaoUrl = createPlatformUrl(siteUrl, KAKAO_SOURCE, KAKAO_MEDIUM, shareToken);
        String instagramUrl = createPlatformUrl(siteUrl, INSTAGRAM_SOURCE, INSTAGRAM_MEDIUM, shareToken);

        return new ShareUrls(shareToken, siteUrl, qrUrl, kakaoUrl, instagramUrl);
    }

    private String qrUrl(String siteUrl, String shareToken) {
        return UriComponentsBuilder.fromUriString(siteUrl).path("/share/{shareToken}").build(shareToken).toString();
    }

    private String normalizeSiteUrl(String siteUrl) {
        return siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    private String createPlatformUrl(String siteUrl, String source, String medium, String shareToken) {
        return UriComponentsBuilder.fromUriString(siteUrl).queryParam("utm_source", source)
            .queryParam("utm_medium", medium).queryParam("utm_campaign", COMMUNITY_MEMO_RESULT_CAMPAIGN)
            .queryParam("share_token", shareToken).build().toUriString();
    }

    record ShareUrls(String shareToken, String siteUrl, String qrUrl, String kakaoUrl, String instagramUrl) {
    }
}
