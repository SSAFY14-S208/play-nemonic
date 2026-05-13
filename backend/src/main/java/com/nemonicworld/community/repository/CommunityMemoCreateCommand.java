package com.nemonicworld.community.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * community_memo insert에 필요한 값 묶음입니다.
 *
 * <p>
 * bodyImageUrl은 게시 원본 이미지 객체 키, thumbnailImageUrl은 게시 썸네일 이미지 객체 키를 담습니다.
 */
public record CommunityMemoCreateCommand(UUID memoId, UUID userId, UUID artifactId, String bodyImageUrl,
    String thumbnailImageUrl, double positionX, double positionY, int zIndex, float rotationDeg, String decoration,
    String ocrText, String ocrCategories, LocalDateTime moderationCheckedAt, LocalDateTime attachedAt,
    LocalDateTime createdAt, LocalDateTime updatedAt) {
}
