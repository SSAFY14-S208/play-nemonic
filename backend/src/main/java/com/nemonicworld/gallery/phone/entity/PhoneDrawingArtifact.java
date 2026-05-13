package com.nemonicworld.gallery.phone.entity;

import java.time.LocalDateTime;
import java.util.UUID;

public record PhoneDrawingArtifact(UUID galleryId, UUID artifactId, String kind, String thumbnailObjectKey,
    String contentObjectKey, LocalDateTime createdAt) {
}
