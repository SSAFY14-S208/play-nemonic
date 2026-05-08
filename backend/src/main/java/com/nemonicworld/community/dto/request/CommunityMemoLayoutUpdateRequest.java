package com.nemonicworld.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 커뮤니티 메모 배치 수정 요청입니다.
 *
 * <p>
 * 이미지와 decoration은 건드리지 않고 캔버스 배치 값만 바꾸는 1차 수정 계약입니다.
 */
@Schema(description = "커뮤니티 메모 배치 수정 요청")
public record CommunityMemoLayoutUpdateRequest(@Schema(description = "캔버스 X 좌표", example = "120.5") Double positionX,
    @Schema(description = "캔버스 Y 좌표", example = "-30.0") Double positionY,
    @Schema(description = "렌더링 z-index", example = "12") Integer zIndex,
    @Schema(description = "회전 각도", example = "5.5") Double rotationDeg) {
}
