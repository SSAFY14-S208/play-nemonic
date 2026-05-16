package com.nemonicworld.infinitecanvas.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 출력 이미지 저장 요청")
public record InfiniteCanvasOutputSaveRequest(
    @Schema(description = "출력 원본 이미지 파일 ID", example = "550e8400-e29b-41d4-a716-446655440000") String imageFileId,
    @Schema(description = "출력 썸네일 이미지 파일 ID", nullable = true) String thumbnailFileId,
    @Schema(description = "프론트 보조 메타데이터", nullable = true) JsonNode meta) {
}
