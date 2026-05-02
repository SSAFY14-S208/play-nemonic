package com.nemonicworld.gallery.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "내 갤러리 항목 상세 조회 응답")
/**
 * 갤러리 상세 화면에 필요한 결과물 식별자, URL, 메타데이터를 함께 전달하는 응답 DTO입니다.
 */
public record GalleryDetailResponse(
    @Schema(description = "갤러리 보관 항목 UUID", example = "8d25f3a5-3c5a-4f21-9f54-68fa4a402011") String galleryId,
    @Schema(description = "결과물 UUID", example = "1bce93a2-9c38-4604-80f9-b89be3ec2e53") String artifactId,
    @Schema(description = "결과물 종류", example = "fortune") String kind,
    @Schema(description = "썸네일 URL", example = "https://minio.example.com/fortune/thumb.png") String thumbnailUrl,
    @Schema(description = "상세 화면 대표 콘텐츠 URL", example = "https://minio.example.com/fortune/result.png") String contentUrl,
    @Schema(description = "결과물 생성 출처 식별자", example = "ROOM123", nullable = true) String sourceRoomId,
    @Schema(description = "결과물 메타데이터") Map<String, Object> meta,
    @Schema(description = "결과물 생성 시각", example = "2026-04-30T13:50:00") LocalDateTime createdAt,
    @Schema(description = "결과물 수정 시각", example = "2026-04-30T13:55:00") LocalDateTime updatedAt) {
}
