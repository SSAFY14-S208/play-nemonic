package com.nemonicworld.community.repository;

import java.util.UUID;

/**
 * 갤러리 기반 커뮤니티 메모 생성 시 원본 출처로 연결할 gallery/artifact 식별 정보입니다.
 */
public record CommunityMemoSourceGalleryRow(UUID galleryId, UUID artifactId, String artifactKind) {
}
