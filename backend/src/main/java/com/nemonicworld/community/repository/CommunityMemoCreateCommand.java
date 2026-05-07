package com.nemonicworld.community.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public record CommunityMemoCreateCommand(UUID memoId, UUID userId, String bodyImageUrl, double positionX,
    double positionY, int zIndex, float rotationDeg, String decoration, LocalDateTime attachedAt,
    LocalDateTime createdAt, LocalDateTime updatedAt) {
}
