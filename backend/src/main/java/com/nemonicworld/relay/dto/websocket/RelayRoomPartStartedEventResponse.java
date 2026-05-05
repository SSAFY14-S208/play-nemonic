package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Relay part started WebSocket event payload")
public record RelayRoomPartStartedEventResponse(@Schema(description = "Room code", example = "AB3K9Q") String roomCode,
    @Schema(description = "Previous part", example = "FACE") RelayDrawingPart previousPart,
    @Schema(description = "Started part", example = "BODY") RelayDrawingPart part,
    @Schema(description = "Part started at", example = "2026-05-05T14:00:31") LocalDateTime partStartedAt,
    @Schema(description = "Part deadline at", example = "2026-05-05T14:01:16") LocalDateTime partDeadlineAt,
    @Schema(description = "Part time limit seconds", example = "45") int timeLimitSeconds) {

    public static RelayRoomPartStartedEventResponse from(RelayRoomSubmissionResponse response) {
        return new RelayRoomPartStartedEventResponse(response.roomCode(), response.part(), response.nextPart(),
            response.nextPartStartedAt(), response.nextPartDeadlineAt(),
            (int) java.time.Duration.between(response.nextPartStartedAt(), response.nextPartDeadlineAt()).toSeconds());
    }
}
