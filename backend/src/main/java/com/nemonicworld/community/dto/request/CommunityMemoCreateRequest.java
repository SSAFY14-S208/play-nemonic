package com.nemonicworld.community.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "커뮤니티 메모 생성 요청")
public record CommunityMemoCreateRequest(@Schema(description = "메모 출처", example = "DIRECT") String sourceType,
    @Schema(description = "파일 ID", example = "550e8400-e29b-41d4-a716-446655440000") String fileId,
    @Schema(description = "갤러리 결과물 ID", nullable = true) String galleryId,
    @Schema(description = "캔버스 X 좌표", example = "0.0") Double positionX,
    @Schema(description = "캔버스 Y 좌표", example = "0.0") Double positionY,
    @Schema(description = "렌더링 z-index", example = "1") Integer zIndex,
    @Schema(description = "회전 각도", example = "5.5") Double rotationDeg,
    @Schema(description = "프론트에서 해석할 메모 데코레이션 JSON 객체", nullable = true) JsonNode decoration) {
}
