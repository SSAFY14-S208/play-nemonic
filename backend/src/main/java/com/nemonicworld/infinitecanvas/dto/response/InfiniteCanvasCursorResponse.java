package com.nemonicworld.infinitecanvas.dto.response;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasCursor;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 커서 갱신 응답")
public record InfiniteCanvasCursorResponse(@Schema(description = "캔버스 ID") String canvasId,
    @Schema(description = "갱신된 커서") InfiniteCanvasCursor cursor) {
}
