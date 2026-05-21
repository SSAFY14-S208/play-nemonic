package com.nemonicworld.infinitecanvas.dto.response;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasLock;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 요소 lock 응답")
public record InfiniteCanvasLockResponse(@Schema(description = "공유 방코드") String roomCode,
    @Schema(description = "요소 ID") String elementId,
    @Schema(description = "현재 lock", nullable = true) InfiniteCanvasLock lock) {
}
