package com.nemonicworld.infinitecanvas.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public record InfiniteCanvasOutputCreateCommand(UUID galleryId, UUID artifactId, UUID userId, String kind,
    String canvasId, String imageObjectKey, String thumbnailObjectKey, String meta, LocalDateTime createdAt,
    LocalDateTime updatedAt) {
}
