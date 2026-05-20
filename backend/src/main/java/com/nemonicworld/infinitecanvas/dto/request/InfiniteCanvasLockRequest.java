package com.nemonicworld.infinitecanvas.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 요소 lock 요청")
public record InfiniteCanvasLockRequest(@Schema(description = "요소 ID", example = "shape-1") String elementId) {
}
