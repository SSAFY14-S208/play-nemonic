package com.nemonicworld.artifact.service.share;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ArtifactShareUrlSupport {

    private static final String KAKAO_SOURCE = "kakao";
    private static final String INSTAGRAM_SOURCE = "instagram";
    private static final String KAKAO_MEDIUM = "social";
    private static final String INSTAGRAM_MEDIUM = "story";

    public String normalizeSiteUrl(String siteUrl) {
        return siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    public String createKakaoUrl(String siteUrl, String campaign, String shareToken) {
        return createPlatformUrl(siteUrl, KAKAO_SOURCE, KAKAO_MEDIUM, campaign, shareToken);
    }

    public String createInstagramUrl(String siteUrl, String campaign, String shareToken) {
        return createPlatformUrl(siteUrl, INSTAGRAM_SOURCE, INSTAGRAM_MEDIUM, campaign, shareToken);
    }

    private String createPlatformUrl(String siteUrl, String source, String medium, String campaign, String shareToken) {
        return UriComponentsBuilder.fromUriString(siteUrl).queryParam("utm_source", source)
            .queryParam("utm_medium", medium).queryParam("utm_campaign", campaign).queryParam("share_token", shareToken)
            .build().toUriString();
    }
}
