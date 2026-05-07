package com.nemonicworld.relay.dto.response;

import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.redis.RelayRoomState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "내 릴레이 현재 배정 조회 응답")
public record RelayRoomMyAssignmentResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "현재 사용자가 그릴 캔버스 번호", example = "1") int canvasIndex,
    @Schema(description = "현재 사용자가 그릴 파트", example = "BODY") RelayDrawingPart part,
    @Schema(description = "현재 배정 상태", example = "PENDING") RelayAssignmentStatus assignmentStatus,
    @Schema(description = "파트 제한 시간(초)", example = "45") int timeLimitSeconds,
    @Schema(description = "현재 파트 시작 시각", example = "2026-05-05T14:00:00") LocalDateTime partStartedAt,
    @Schema(description = "현재 파트 마감 시각", example = "2026-05-05T14:00:45") LocalDateTime partDeadlineAt,
    @Schema(description = "서버 기준 남은 시간(초)", example = "32") long remainingSeconds,
    @Schema(description = "이전 파트 힌트") RelayRoomAssignmentHintResponse hint) {

    public static RelayRoomMyAssignmentResponse from(RelayRoomState roomState, RelayRoomAssignment assignment,
        long remainingSeconds, RelayRoomAssignmentHintResponse hint) {
        return new RelayRoomMyAssignmentResponse(roomState.roomCode(), assignment.canvasIndex(), assignment.part(),
            assignment.status(), roomState.timeLimitSeconds(), roomState.partStartedAt(), roomState.partDeadlineAt(),
            remainingSeconds, hint);
    }
}
