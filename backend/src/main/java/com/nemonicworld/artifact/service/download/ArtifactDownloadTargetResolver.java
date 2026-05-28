package com.nemonicworld.artifact.service.download;

import com.nemonicworld.artifact.repository.ArtifactImageUrlRow;
import com.nemonicworld.common.exception.BadRequestException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ArtifactDownloadTargetResolver {

    private static final String UNSUPPORTED_ARTIFACT_KIND_MESSAGE = "다운로드할 수 없는 산출물 종류입니다.";
    private static final String SHARE_IMAGE_REQUIRED_MESSAGE = "다운로드할 이미지 URL이 없습니다.";
    private static final String EXTERNAL_OBJECT_REFERENCE_MESSAGE = "QR 합성 다운로드는 MinIO 산출물만 지원합니다.";
    private static final String KIND_FORTUNE = "fortune";
    private static final String KIND_RELAY_DRAWING = "relay_drawing";
    private static final String KIND_FLIPBOOK = "flipbook";
    private static final String KIND_INFINITE_CANVAS = "infinite_canvas";
    private static final String KIND_PHONE = "phone";
    private static final String KIND_COMMUNITY_MEMO = "community_memo";
    private static final Set<String> DOWNLOADABLE_KINDS = Set.of(KIND_FORTUNE, KIND_RELAY_DRAWING, KIND_FLIPBOOK,
        KIND_INFINITE_CANVAS, KIND_PHONE, KIND_COMMUNITY_MEMO);

    public ArtifactDownloadTarget resolve(ArtifactImageUrlRow row) {
        String objectKey = selectDownloadObjectKey(row);
        validateDownloadTarget(row, objectKey);

        return new ArtifactDownloadTarget(objectKey, sourceContentType(row.kind()), resultContentType(row.kind()),
            extension(row.kind()));
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
        if (KIND_INFINITE_CANVAS.equals(row.kind())) {
            return firstText(row.infiniteCanvasImageUrl(), row.thumbnailUrl());
        }
        if (KIND_PHONE.equals(row.kind())) {
            return firstText(row.phoneImageUrl(), row.thumbnailUrl());
        }
        if (KIND_COMMUNITY_MEMO.equals(row.kind())) {
            return firstText(row.communityMemoOriginalImageUrl(),
                firstText(row.communityMemoThumbnailImageUrl(), row.thumbnailUrl()));
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

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
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

    private boolean isAbsoluteUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
