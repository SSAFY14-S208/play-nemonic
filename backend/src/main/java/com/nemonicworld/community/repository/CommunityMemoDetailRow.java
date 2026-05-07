package com.nemonicworld.community.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 커뮤니티 메모 상세 조회에 필요한 DB row 모델입니다.
 */
public record CommunityMemoDetailRow(UUID memoId, UUID userId, String authorNickname, UUID artifactId,
    String artifactKind, String imageReference, double positionX, double positionY, int zIndex, float rotationDeg,
    String decoration, int reportCount, String moderationStatus, LocalDateTime attachedAt, LocalDateTime createdAt,
    LocalDateTime updatedAt) {
}
