package com.nemonicworld.gallery.dto.response;

import java.time.LocalDateTime;

/**
 * 갤러리 항목 삭제 성공 시 삭제된 보관 관계 정보를 전달하는 응답 DTO입니다.
 */
public record GalleryDeleteResponse(String galleryId, String artifactId, LocalDateTime deletedAt) {
}
