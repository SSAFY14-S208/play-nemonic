package com.nemonicworld.community.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public record CommunityMemoCreateCommand(UUID memoId, UUID userId, UUID artifactId, String bodyImageUrl,
    String thumbnailImageUrl, double positionX, double positionY, int zIndex, float rotationDeg, String decoration,
    String ocrText, String ocrCategories, LocalDateTime moderationCheckedAt, LocalDateTime attachedAt,
    LocalDateTime createdAt, LocalDateTime updatedAt) {
}
