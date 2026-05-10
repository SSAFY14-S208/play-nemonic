package com.nemonicworld.artifact.service.download;

import com.nemonicworld.artifact.repository.ArtifactImageUrlRepository;
import com.nemonicworld.artifact.repository.ArtifactImageUrlRow;
import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.common.exception.NotFoundException;
import com.nemonicworld.share.config.ShareProperties;
import com.nemonicworld.share.service.SignedShareTokenIssuer;
import com.nemonicworld.user.service.AnonymousUserResolver;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ArtifactQrAssetServiceImpl implements ArtifactQrAssetService {

    private static final String INVALID_ARTIFACT_ID_MESSAGE = "유효하지 않은 산출물 ID 형식입니다.";
    private static final String ARTIFACT_DOWNLOAD_NOT_FOUND_MESSAGE = "다운로드 가능한 산출물을 찾을 수 없습니다.";
    private static final String UNSUPPORTED_ARTIFACT_KIND_MESSAGE = "다운로드할 수 없는 산출물 종류입니다.";
    private static final String SHARE_IMAGE_REQUIRED_MESSAGE = "다운로드할 이미지 URL이 없습니다.";
    private static final String EXTERNAL_OBJECT_REFERENCE_MESSAGE = "QR 합성 다운로드는 MinIO 산출물만 지원합니다.";
    private static final String KIND_FORTUNE = "fortune";
    private static final String KIND_RELAY_DRAWING = "relay_drawing";
    private static final String KIND_FLIPBOOK = "flipbook";
    private static final String KIND_COMMUNITY_MEMO = "community_memo";
    private static final String CHANNEL_QR_DOWNLOAD = "QR_DOWNLOAD";
    private static final Set<String> DOWNLOADABLE_KINDS = Set.of(KIND_FORTUNE, KIND_RELAY_DRAWING, KIND_FLIPBOOK,
        KIND_COMMUNITY_MEMO);

    private final ArtifactImageUrlRepository artifactImageUrlRepository;
    private final AnonymousUserResolver anonymousUserResolver;
    private final ArtifactDownloadStorage artifactDownloadStorage;
    private final ArtifactQrComposer artifactQrComposer;
    private final SignedShareTokenIssuer signedShareTokenIssuer;
    private final ShareProperties shareProperties;

    public ArtifactQrAssetServiceImpl(ArtifactImageUrlRepository artifactImageUrlRepository,
        AnonymousUserResolver anonymousUserResolver, ArtifactDownloadStorage artifactDownloadStorage,
        ArtifactQrComposer artifactQrComposer, SignedShareTokenIssuer signedShareTokenIssuer,
        ShareProperties shareProperties) {
        this.artifactImageUrlRepository = artifactImageUrlRepository;
        this.anonymousUserResolver = anonymousUserResolver;
        this.artifactDownloadStorage = artifactDownloadStorage;
        this.artifactQrComposer = artifactQrComposer;
        this.signedShareTokenIssuer = signedShareTokenIssuer;
        this.shareProperties = shareProperties;
    }

    @Transactional
    @Override
    public ArtifactQrAsset prepareQrAsset(String userUuidValue, String artifactIdValue) {
        UUID userUuid = anonymousUserResolver.parseUuid(userUuidValue);
        UUID artifactId = parseArtifactId(artifactIdValue);

        anonymousUserResolver.resolve(userUuid);

        ArtifactImageUrlRow row = artifactImageUrlRepository.findActiveArtifactImageUrl(artifactId, userUuid)
            .orElseThrow(() -> new NotFoundException(ARTIFACT_DOWNLOAD_NOT_FOUND_MESSAGE));
        String objectKey = selectDownloadObjectKey(row);
        validateDownloadTarget(row, objectKey);

        String contentType = resultContentType(row.kind());
        String extension = extension(row.kind());
        String shareToken = signedShareTokenIssuer.issueArtifactToken(row.artifactId(), row.kind(),
            CHANNEL_QR_DOWNLOAD);
        String cacheObjectKey = "artifact-downloads/%s/result-qr.%s".formatted(artifactId, extension);
        String fileName = "nemonic-%s.%s".formatted(artifactId, extension);

        if (!artifactDownloadStorage.exists(cacheObjectKey)) {
            byte[] sourceBytes = artifactDownloadStorage.download(objectKey.trim());
            byte[] composedBytes = artifactQrComposer.compose(sourceContentType(row.kind()), sourceBytes,
                qrUrl(shareToken));
            artifactDownloadStorage.upload(cacheObjectKey, composedBytes, contentType);
        }

        return new ArtifactQrAsset(row.artifactId(), row.kind(), cacheObjectKey, fileName, contentType, shareToken);
    }

    private String selectDownloadObjectKey(ArtifactImageUrlRow row) {
        if (KIND_FORTUNE.equals(row.kind())) {
            return firstText(row.fortuneImageUrl(), row.thumbnailUrl());
        }
        if (KIND_RELAY_DRAWING.equals(row.kind())) {
            return firstText(row.relayCombinedPreviewUrl(), row.thumbnailUrl());
        }
        if (KIND_FLIPBOOK.equals(row.kind())) {
            return row.flipbookGifUrl();
        }
        if (KIND_COMMUNITY_MEMO.equals(row.kind())) {
            return row.thumbnailUrl();
        }

        return row.thumbnailUrl();
    }

    private void validateDownloadTarget(ArtifactImageUrlRow row, String objectKey) {
        if (!DOWNLOADABLE_KINDS.contains(row.kind())) {
            throw new BadRequestException(UNSUPPORTED_ARTIFACT_KIND_MESSAGE);
        }
        if (!StringUtils.hasText(objectKey)) {
            throw new BadRequestException(SHARE_IMAGE_REQUIRED_MESSAGE);
        }
        if (isAbsoluteUrl(objectKey)) {
            throw new BadRequestException(EXTERNAL_OBJECT_REFERENCE_MESSAGE);
        }
    }

    private String qrUrl(String shareToken) {
        return UriComponentsBuilder.fromUriString(normalizeSiteUrl(shareProperties.siteUrl()))
            .path("/share/{shareToken}").build(shareToken).toString();
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String normalizeSiteUrl(String siteUrl) {
        return siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    private String sourceContentType(String kind) {
        if (KIND_FLIPBOOK.equals(kind)) {
            return "image/gif";
        }

        return "image/png";
    }

    private String resultContentType(String kind) {
        if (KIND_FLIPBOOK.equals(kind)) {
            return "image/gif";
        }

        return "image/jpeg";
    }

    private String extension(String kind) {
        if (KIND_FLIPBOOK.equals(kind)) {
            return "gif";
        }

        return "jpg";
    }

    private UUID parseArtifactId(String artifactIdValue) {
        if (!StringUtils.hasText(artifactIdValue)) {
            throw new BadRequestException(INVALID_ARTIFACT_ID_MESSAGE);
        }

        try {
            return UUID.fromString(artifactIdValue);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_ARTIFACT_ID_MESSAGE);
        }
    }

    private boolean isAbsoluteUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
