package com.nemonicworld.gallery.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 갤러리 목록 native query 결과를 서비스 계층으로 전달하는 읽기 전용 행 모델입니다.
 */
public record GalleryItemRow(UUID galleryId, UUID artifactId, String kind, String thumbnailUrl, String contentUrl,
    String sourceRoomId, LocalDateTime createdAt) {
}
