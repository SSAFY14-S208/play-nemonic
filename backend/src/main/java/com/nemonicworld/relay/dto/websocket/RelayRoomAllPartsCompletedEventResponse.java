package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "릴레이 전체 파트 완료 WebSocket 이벤트")
public record RelayRoomAllPartsCompletedEventResponse(@Schema(description = "방 코드") String roomCode,
    @Schema(description = "전체 완료 후 방 상태", example = "FINALIZING") RelayRoomStatus roomStatus,
    @Schema(description = "완료 시각", example = "2026-05-05T14:02:01") LocalDateTime completedAt) {

    public static RelayRoomAllPartsCompletedEventResponse from(RelayRoomSubmissionResponse response) {
        return new RelayRoomAllPartsCompletedEventResponse(response.roomCode(), response.roomStatus(),
            response.submittedAt());
    }
}
