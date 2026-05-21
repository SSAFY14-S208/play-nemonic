package com.nemonicworld.flipbook.dto.websocket;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.LocalDateTime;

@Schema(description = "플립북 라운드 시작 WebSocket 이벤트")
public record FlipbookRoundStartedEventResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "이전 라운드", example = "1") Integer previousRound,
    @Schema(description = "시작 라운드", example = "2") Integer round,
    @Schema(description = "라운드 시작 시각", example = "2026-05-08T14:00:31") LocalDateTime roundStartedAt,
    @Schema(description = "라운드 마감 시각", example = "2026-05-08T14:01:16") LocalDateTime roundDeadlineAt,
    @Schema(description = "라운드 제한 시간(초)", example = "45") int timeLimitSeconds) {

    public static FlipbookRoundStartedEventResponse of(String roomCode, Integer previousRound, Integer round,
        LocalDateTime roundStartedAt, LocalDateTime roundDeadlineAt) {
        int timeLimitSeconds = (int) Duration.between(roundStartedAt, roundDeadlineAt).toSeconds();

        return new FlipbookRoundStartedEventResponse(roomCode, previousRound, round, roundStartedAt, roundDeadlineAt,
            timeLimitSeconds);
    }
}
