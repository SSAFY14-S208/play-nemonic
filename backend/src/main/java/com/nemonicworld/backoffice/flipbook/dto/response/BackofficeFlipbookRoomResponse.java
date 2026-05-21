package com.nemonicworld.backoffice.flipbook.dto.response;

import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "백오피스 활성 플립북 방 응답")
public record BackofficeFlipbookRoomResponse(@Schema(description = "공유 방코드", example = "AB3K9Q") String roomCode,

    @Schema(description = "방 상태 (CLOSED 제외)", example = "PLAYING") FlipbookRoomStatus status,

    @Schema(description = "현재 참여자 수 (드롭/끊김 무관 전체 수)", example = "4") int participantCount,

    @Schema(description = "현재 라운드. WAITING 상태에서는 null", example = "3") Integer currentRound,

    @Schema(description = "전체 라운드 수. WAITING 상태에서는 null", example = "8") Integer totalRounds,

    @Schema(description = "게임 시작 시각. WAITING 상태에서는 null", example = "2026-05-09T12:00:00") LocalDateTime gameStartedAt) {

    public static BackofficeFlipbookRoomResponse from(FlipbookRoomState roomState) {
        return new BackofficeFlipbookRoomResponse(roomState.roomCode(), roomState.status(),
            roomState.participantCount(), roomState.currentRound(), roomState.totalRounds(), roomState.gameStartedAt());
    }
}
