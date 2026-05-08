package com.nemonicworld.phone.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "휴대폰 그림 갤러리 저장 요청")
public record PhoneDrawingSaveRequest(
    @Schema(description = "원본 이미지 파일 ID", example = "550e8400-e29b-41d4-a716-446655440000") String imageFileId,
    @Schema(description = "썸네일 이미지 파일 ID", nullable = true) String thumbnailFileId,
    @Schema(description = "프론트 보조 메타데이터", nullable = true) JsonNode meta) {
}
