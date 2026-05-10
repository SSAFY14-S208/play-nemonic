package com.nemonicworld.flipbook.dto.websocket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "플립북 라운드 제한 시간 종료 WebSocket 이벤트")
public record FlipbookRoundTimeUpEventResponse(@Schema(description = "방 코드", example = "FB3K9Q") String roomCode,
    @Schema(description = "제한 시간이 종료된 라운드", example = "2") int round,
    @Schema(description = "라운드 마감 시각", example = "2026-05-08T14:01:16") LocalDateTime roundDeadlineAt,
    @Schema(description = "자동 제출 전까지 제출을 허용하는 시각", example = "2026-05-08T14:01:18") LocalDateTime submitGraceDeadlineAt,
    @Schema(description = "자동 제출 유예 시간(ms)", example = "2000") long autoSubmitGraceMillis) {
}
