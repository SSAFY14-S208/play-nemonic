package com.nemonicworld.community.service.memo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nemonicworld.community.dto.response.CommunityMemoDetailResponse;
import com.nemonicworld.community.dto.response.CommunityMemoItemResponse;
import com.nemonicworld.community.entity.CommunityMemoSourceType;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import com.nemonicworld.community.repository.CommunityMemoRow;
import com.nemonicworld.community.service.support.CommunityMemoEventLogger;
import com.nemonicworld.global.storage.minio.MinioPublicUrlResolver;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.nemonicworld.community.service.support.CommunityMemoEventLogger.metadata;

/**
 * 커뮤니티 메모 DB row를 API 응답 DTO로 변환합니다.
 */
@Component
class CommunityMemoResponseMapper {

    private static final Logger log = LoggerFactory.getLogger(CommunityMemoResponseMapper.class);
    private static final TypeReference<Map<String, Object>> DECORATION_TYPE = new TypeReference<>() {
    };
    private static final String KIND_FLIPBOOK = "flipbook";

    private final MinioPublicUrlResolver minioPublicUrlResolver;
    private final ObjectMapper objectMapper;

    CommunityMemoResponseMapper(MinioPublicUrlResolver minioPublicUrlResolver, ObjectMapper objectMapper) {
        this.minioPublicUrlResolver = minioPublicUrlResolver;
        this.objectMapper = objectMapper;
    }

    CommunityMemoItemResponse toResponse(CommunityMemoRow row, UUID viewerUserUuid) {
        String sourceType = resolveSourceType(row.artifactId());
        String memoOriginalImageUrl = resolveMemoImageUrl(row.originalImageReference(), row.memoId(), "original");
        String memoThumbnailImageUrl = resolveMemoImageUrl(row.thumbnailImageReference(), row.memoId(), "thumbnail");
        String memoPlaybackImageUrl = resolveMemoPlaybackImageUrl(row.artifactKind(), row.playbackImageReference(),
            row.memoId());
        String memoImageUrl = representativeImageUrl(memoOriginalImageUrl, memoThumbnailImageUrl);
        boolean ownedByMe = isOwnedByViewer(row.userId(), viewerUserUuid);

        return new CommunityMemoItemResponse(row.memoId().toString(), row.authorNickname(), sourceType, memoImageUrl,
            memoOriginalImageUrl, memoThumbnailImageUrl, memoPlaybackImageUrl, row.positionX(), row.positionY(),
            row.zIndex(), row.rotationDeg(), ownedByMe, row.attachedAt(), parseDecoration(row.decoration()));
    }

    CommunityMemoDetailResponse toDetailResponse(CommunityMemoDetailRow row, UUID viewerUserUuid) {
        String sourceType = resolveSourceType(row.artifactId());
        String memoOriginalImageUrl = resolveMemoImageUrl(row.originalImageReference(), row.memoId(), "original");
        String memoThumbnailImageUrl = resolveMemoImageUrl(row.thumbnailImageReference(), row.memoId(), "thumbnail");
        String memoPlaybackImageUrl = resolveMemoPlaybackImageUrl(row.artifactKind(), row.playbackImageReference(),
            row.memoId());
        String memoImageUrl = representativeImageUrl(memoOriginalImageUrl, memoThumbnailImageUrl);
        boolean ownedByMe = isOwnedByViewer(row.userId(), viewerUserUuid);
        String artifactId = row.artifactId() == null ? null : row.artifactId().toString();
        String galleryContentKind = row.artifactId() == null ? null : row.artifactKind();

        return new CommunityMemoDetailResponse(row.memoId().toString(), row.authorNickname(), sourceType, memoImageUrl,
            memoOriginalImageUrl, memoThumbnailImageUrl, memoPlaybackImageUrl, row.positionX(), row.positionY(),
            row.zIndex(), row.rotationDeg(), ownedByMe, row.attachedAt(), parseDecoration(row.decoration()), artifactId,
            galleryContentKind, row.moderationStatus(), row.reportCount(), row.createdAt(), row.updatedAt());
    }

    boolean isOwnedByViewer(UUID userId, UUID viewerUserUuid) {
        return viewerUserUuid != null && viewerUserUuid.equals(userId);
    }

    String resolveMemoImageUrl(String objectKey, UUID memoId, String imageRole) {
        String imageUrl = minioPublicUrlResolver.resolve(objectKey);
        if (StringUtils.hasText(objectKey) && !StringUtils.hasText(imageUrl)) {
            CommunityMemoEventLogger.warn(
                "community_file_url_resolve_failed", "community memo image url resolve failed", metadata("memo_id",
                    memoId, "image_role", imageRole, "object_key_hash", CommunityMemoEventLogger.hash(objectKey)),
                null);
        }

        return imageUrl;
    }

    private String resolveMemoPlaybackImageUrl(String artifactKind, String objectKey, UUID memoId) {
        if (!KIND_FLIPBOOK.equals(artifactKind) || !StringUtils.hasText(objectKey)) {
            return null;
        }

        String imageUrl = minioPublicUrlResolver.resolve(objectKey);
        if (!StringUtils.hasText(imageUrl)) {
            CommunityMemoEventLogger.warn("community_file_url_resolve_failed",
                "community memo playback image url resolve failed", metadata("memo_id", memoId, "image_role",
                    "playback", "object_key_hash", CommunityMemoEventLogger.hash(objectKey)),
                null);
        }

        return imageUrl;
    }

    private String representativeImageUrl(String memoOriginalImageUrl, String memoThumbnailImageUrl) {
        return memoThumbnailImageUrl == null ? memoOriginalImageUrl : memoThumbnailImageUrl;
    }

    private String resolveSourceType(UUID artifactId) {
        return artifactId == null ? CommunityMemoSourceType.DIRECT.value() : CommunityMemoSourceType.GALLERY.value();
    }

    private Map<String, Object> parseDecoration(String decoration) {
        if (!StringUtils.hasText(decoration)) {
            return Map.of();
        }

        try {
            Map<String, Object> parsedDecoration = objectMapper.readValue(decoration, DECORATION_TYPE);
            return parsedDecoration == null ? Map.of() : parsedDecoration;
        } catch (JsonProcessingException e) {
            CommunityMemoEventLogger.warn("community_decoration_parse_failed", "community memo decoration parse failed",
                metadata("decoration_length", decoration.length(), "reason_code", "parse_failed"), e);
            log.warn("community memo decoration JSON parse failed. decorationLength={}", decoration.length(), e);

            return Map.of();
        }
    }
}
