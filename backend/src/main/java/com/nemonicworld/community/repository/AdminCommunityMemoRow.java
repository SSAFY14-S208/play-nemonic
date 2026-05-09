package com.nemonicworld.community.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 관리자 커뮤니티 메모 조회에 필요한 운영용 DB row projection입니다.
 */
public record AdminCommunityMemoRow(UUID memoId, UUID userId, String authorNickname, UUID artifactId,
    String artifactKind, String originalImageReference, String thumbnailImageReference, double positionX,
    double positionY, int zIndex, float rotationDeg, String decoration, int reportCount, boolean hidden,
    String hiddenReason, LocalDateTime hiddenAt, String moderationStatus, String ocrText, String ocrCategories,
    Long reviewedBy, LocalDateTime attachedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
