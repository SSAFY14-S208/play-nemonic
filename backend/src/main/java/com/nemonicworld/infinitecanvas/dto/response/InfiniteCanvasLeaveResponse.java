package com.nemonicworld.infinitecanvas.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "무한 캔버스 퇴장 응답")
public record InfiniteCanvasLeaveResponse(@Schema(description = "캔버스 ID") String canvasId,
    @Schema(description = "퇴장한 사용자 UUID") String userUuid,
    @Schema(description = "마지막 참여자 퇴장으로 캔버스가 종료되었는지") boolean closed,
    @Schema(description = "종료 시각", nullable = true) LocalDateTime closedAt) {
}
