package com.nemonicworld.backoffice.relay.dto.response;

import com.nemonicworld.relay.entity.RelayRoomStatus;
import com.nemonicworld.relay.redis.RelayRoomState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "백오피스 활성 릴레이 드로잉 방 응답")
public record BackofficeRelayRoomResponse(@Schema(description = "공유 방코드", example = "AB3K9Q") String roomCode,

    @Schema(description = "방 상태 (CLOSED 제외)", example = "PLAYING") RelayRoomStatus status,

    @Schema(description = "현재 참여자 수 (드롭/끊김 무관 전체 수)", example = "4") int participantCount,

    @Schema(description = "게임 시작 시각. WAITING 상태에서는 null", example = "2026-05-09T12:00:00") LocalDateTime gameStartedAt) {

    public static BackofficeRelayRoomResponse from(RelayRoomState roomState) {
        return new BackofficeRelayRoomResponse(roomState.roomCode(), roomState.status(), roomState.participantCount(),
            roomState.gameStartedAt());
    }
}
