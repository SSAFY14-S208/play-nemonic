package com.nemonicworld.community.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 커뮤니티 메모 생성 요청입니다.
 *
 * <p>
 * originalFileId와 thumbnailFileId는 모두 files API에서 COMMUNITY 목적으로 업로드 후 confirm된
 * 파일이어야 합니다.
 */
@Schema(description = "커뮤니티 메모 생성 요청")
public record CommunityMemoCreateRequest(@Schema(description = "메모 출처", example = "DIRECT") String sourceType,
    @Schema(description = "최종 원본 이미지 파일 ID", example = "550e8400-e29b-41d4-a716-446655440000") String originalFileId,
    @Schema(description = "최종 썸네일 이미지 파일 ID", example = "660e8400-e29b-41d4-a716-446655440000") String thumbnailFileId,
    @Schema(description = "갤러리 원본 항목 ID", nullable = true) String sourceGalleryId,
    @Schema(description = "캔버스 X 좌표", example = "0.0") Double positionX,
    @Schema(description = "캔버스 Y 좌표", example = "0.0") Double positionY,
    @Schema(description = "렌더링 z-index", example = "1") Integer zIndex,
    @Schema(description = "회전 각도", example = "5.5") Double rotationDeg,
    @Schema(description = "프론트에서 해석할 메모 데코레이션 JSON 객체", nullable = true) JsonNode decoration,
    @Schema(description = "OCR 보조 입력용 클라이언트 텍스트", nullable = true) String clientText) {
}
