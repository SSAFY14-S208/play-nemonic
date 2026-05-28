package com.nemonicworld.community.service.memo;

import com.nemonicworld.common.exception.BadRequestException;
import com.nemonicworld.community.entity.CommunityMemoModerationStatus;
import com.nemonicworld.community.repository.CommunityMemoDetailRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class CommunityMemoShareSourceResolver {

    static final String IMAGE_REQUIRED_MESSAGE = "커뮤니티 메모 공유 이미지가 없습니다.";

    private static final String COMMUNITY_MEMO_SHARE_BLOCKED_MESSAGE = "공유할 수 없는 커뮤니티 메모입니다.";
    private static final String EXTERNAL_OBJECT_REFERENCE_MESSAGE = "QR 합성 공유는 MinIO 오브젝트 키 기반 이미지만 지원합니다.";
    private static final String KIND_FLIPBOOK = "flipbook";
    private static final String STATIC_IMAGE_CONTENT_TYPE = "image/png";
    private static final String STATIC_SHARE_CONTENT_TYPE = "image/jpeg";
    private static final String GIF_CONTENT_TYPE = "image/gif";

    ShareSource resolve(CommunityMemoDetailRow row) {
        validateShareable(row);
        return selectShareSource(row);
    }

    private void validateShareable(CommunityMemoDetailRow row) {
        if (CommunityMemoModerationStatus.BLOCKED.value().equals(row.moderationStatus())) {
            throw new BadRequestException(COMMUNITY_MEMO_SHARE_BLOCKED_MESSAGE);
        }
    }

    private ShareSource selectShareSource(CommunityMemoDetailRow row) {
        if (isGifShare(row)) {
            String objectKey = validateSourceObjectKey(row.playbackImageReference());

            return new ShareSource(objectKey, GIF_CONTENT_TYPE, GIF_CONTENT_TYPE, "gif");
        }

        String objectKey = validateSourceObjectKey(
            firstText(row.originalImageReference(), row.thumbnailImageReference()));

        return new ShareSource(objectKey, STATIC_IMAGE_CONTENT_TYPE, STATIC_SHARE_CONTENT_TYPE, "jpg");
    }

    private String validateSourceObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BadRequestException(IMAGE_REQUIRED_MESSAGE);
        }
        String trimmedObjectKey = objectKey.trim();
        if (isAbsoluteUrl(trimmedObjectKey)) {
            throw new BadRequestException(EXTERNAL_OBJECT_REFERENCE_MESSAGE);
        }

        return trimmedObjectKey;
    }

    private boolean isGifShare(CommunityMemoDetailRow row) {
        return KIND_FLIPBOOK.equals(row.artifactKind()) || StringUtils.hasText(row.playbackImageReference());
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private boolean isAbsoluteUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    record ShareSource(String objectKey, String sourceContentType, String resultContentType, String extension) {
    }
}
