package com.nemonicworld.phone.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public record PhoneDrawingCreateCommand(UUID galleryId, UUID artifactId, UUID userId, String kind,
    String imageObjectKey, String thumbnailObjectKey, String meta, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
