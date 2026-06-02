package com.nemonicworld.artifact.service.download;

import com.nemonicworld.share.config.ShareProperties;
import com.nemonicworld.share.service.SignedShareTokenIssuer;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ArtifactQrShareUrlSupport {

    private static final String CHANNEL_QR_DOWNLOAD = "QR_DOWNLOAD";

    private final SignedShareTokenIssuer signedShareTokenIssuer;
    private final ShareProperties shareProperties;

    public ArtifactQrShareUrlSupport(SignedShareTokenIssuer signedShareTokenIssuer, ShareProperties shareProperties) {
        this.signedShareTokenIssuer = signedShareTokenIssuer;
        this.shareProperties = shareProperties;
    }

    public String issueShareToken(UUID artifactId, String kind) {
        return signedShareTokenIssuer.issueArtifactToken(artifactId, kind, CHANNEL_QR_DOWNLOAD);
    }

    public String qrUrl(String shareToken) {
        return UriComponentsBuilder.fromUriString(normalizeSiteUrl(shareProperties.siteUrl()))
            .path("/share/{shareToken}").build(shareToken).toString();
    }

    private String normalizeSiteUrl(String siteUrl) {
        return siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }
}
