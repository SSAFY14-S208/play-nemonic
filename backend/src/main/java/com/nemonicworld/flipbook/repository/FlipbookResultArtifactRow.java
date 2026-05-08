package com.nemonicworld.flipbook.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * gallery 소유권 기준으로 조회한 플립북 결과 artifact row입니다.
 */
public record FlipbookResultArtifactRow(UUID galleryId, UUID artifactId, String thumbnailUrl, String gifUrl,
    String firstImageUrl, String meta, LocalDateTime createdAt) {
}
