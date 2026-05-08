package com.nemonicworld.flipbook.dto.websocket;

import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "플립북 전체 라운드 완료 WebSocket 이벤트")
public record FlipbookAllRoundsCompletedEventResponse(@Schema(description = "방 코드") String roomCode,
    @Schema(description = "전체 완료 후 방 상태", example = "FINISHED") FlipbookRoomStatus roomStatus,
    @Schema(description = "완료 시각", example = "2026-05-08T14:02:01") LocalDateTime completedAt) {
}
