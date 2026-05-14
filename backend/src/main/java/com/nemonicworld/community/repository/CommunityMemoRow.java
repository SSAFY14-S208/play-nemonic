package com.nemonicworld.community.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 커뮤니티 메모 목록 조회에 필요한 DB row 모델입니다.
 */
public record CommunityMemoRow(UUID memoId, UUID userId, String authorNickname, UUID artifactId,
    String originalImageReference, String thumbnailImageReference, double positionX, double positionY, int zIndex,
    float rotationDeg, String decoration, LocalDateTime attachedAt) {
}
