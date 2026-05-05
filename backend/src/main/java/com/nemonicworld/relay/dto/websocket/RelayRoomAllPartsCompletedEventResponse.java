package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Relay all parts completed WebSocket event payload")
public record RelayRoomAllPartsCompletedEventResponse(
    @Schema(description = "Room code", example = "AB3K9Q") String roomCode,
    @Schema(description = "Room status after all parts completed", example = "FINALIZING") RelayRoomStatus roomStatus,
    @Schema(description = "Completed at", example = "2026-05-05T14:02:01") LocalDateTime completedAt) {

    public static RelayRoomAllPartsCompletedEventResponse from(RelayRoomSubmissionResponse response) {
        return new RelayRoomAllPartsCompletedEventResponse(response.roomCode(), response.roomStatus(),
            response.submittedAt());
    }
}
