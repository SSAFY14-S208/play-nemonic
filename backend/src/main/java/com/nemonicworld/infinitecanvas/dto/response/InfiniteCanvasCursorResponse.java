package com.nemonicworld.infinitecanvas.dto.response;

import com.nemonicworld.infinitecanvas.redis.InfiniteCanvasCursor;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "무한 캔버스 커서 갱신 응답")
public record InfiniteCanvasCursorResponse(@Schema(description = "공유 방코드") String roomCode,
    @Schema(description = "갱신된 커서") InfiniteCanvasCursor cursor) {
}
