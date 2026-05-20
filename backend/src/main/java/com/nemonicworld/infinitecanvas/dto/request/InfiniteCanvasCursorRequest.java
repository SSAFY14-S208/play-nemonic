package com.nemonicworld.infinitecanvas.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 커서 위치 갱신 요청")
public record InfiniteCanvasCursorRequest(@Schema(description = "캔버스 X 좌표") Double x,
    @Schema(description = "캔버스 Y 좌표") Double y, @Schema(description = "현재 줌 배율", nullable = true) Double zoom,
    @Schema(description = "프론트 보조 payload", nullable = true) JsonNode payload) {
}
