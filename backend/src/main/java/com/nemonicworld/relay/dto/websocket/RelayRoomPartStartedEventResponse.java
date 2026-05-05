package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.dto.response.RelayRoomSubmissionResponse;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "릴레이 파트 시작 WebSocket 이벤트")
public record RelayRoomPartStartedEventResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "이전 파트", example = "FACE") RelayDrawingPart previousPart,
    @Schema(description = "시작 파트", example = "BODY") RelayDrawingPart part,
    @Schema(description = "파트 시작 시각", example = "2026-05-05T14:00:31") LocalDateTime partStartedAt,
    @Schema(description = "파트 마감 시각", example = "2026-05-05T14:01:16") LocalDateTime partDeadlineAt,
    @Schema(description = "파트 제한 시간(초)", example = "45") int timeLimitSeconds) {

    public static RelayRoomPartStartedEventResponse from(RelayRoomSubmissionResponse response) {
        return new RelayRoomPartStartedEventResponse(response.roomCode(), response.part(), response.nextPart(),
            response.nextPartStartedAt(), response.nextPartDeadlineAt(),
            (int) java.time.Duration.between(response.nextPartStartedAt(), response.nextPartDeadlineAt()).toSeconds());
    }
}
