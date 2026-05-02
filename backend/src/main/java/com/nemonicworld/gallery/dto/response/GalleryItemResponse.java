package com.nemonicworld.gallery.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "내 갤러리 목록 항목 응답")
/**
 * 내 갤러리 목록 화면에서 한 결과물 카드를 구성하는 응답 DTO입니다.
 *
 * DB에 저장된 URL 문자열만 반환하며 MinIO 파일 존재 여부나 presigned URL 발급은 수행하지 않습니다.
 */
public record GalleryItemResponse(
    @Schema(description = "갤러리 보관 항목 UUID", example = "8d25f3a5-3c5a-4f21-9f54-68fa4a402011") String galleryId,
    @Schema(description = "결과물 UUID", example = "1bce93a2-9c38-4604-80f9-b89be3ec2e53") String artifactId,
    @Schema(description = "결과물 종류", example = "fortune") String kind,
    @Schema(description = "목록 썸네일 URL", example = "https://minio.example.com/fortune/thumb.png") String thumbnailUrl,
    @Schema(description = "결과물 대표 콘텐츠 URL", example = "https://minio.example.com/fortune/result.png") String contentUrl,
    @Schema(description = "결과물이 생성된 방 또는 콘텐츠 식별자", example = "ROOM123", nullable = true) String sourceRoomId,
    @Schema(description = "결과물 생성 시각", example = "2026-04-30T13:50:00") LocalDateTime createdAt) {
}
