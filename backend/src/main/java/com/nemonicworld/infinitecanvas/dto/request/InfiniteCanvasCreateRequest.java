package com.nemonicworld.infinitecanvas.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 생성 요청")
public record InfiniteCanvasCreateRequest(@Schema(description = "참여자 닉네임", nullable = true) String nickname,
    @Schema(description = "참여자 색상", nullable = true, example = "#2F80ED") String color,
    @Schema(description = "참여자 아바타 URL", nullable = true) String avatarUrl,
    @Schema(description = "초기 뷰포트 정보", nullable = true) JsonNode viewport) {
}
