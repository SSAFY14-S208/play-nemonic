package com.nemonicworld.relay.dto.websocket;

import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "릴레이 파트 자동 제출 WebSocket 이벤트")
public record RelayRoomPartAutoSubmittedEventResponse(@Schema(description = "방 코드") String roomCode,
    @Schema(description = "자동 제출 사용자 UUID") String userUuid, @Schema(description = "자동 제출 사용자 닉네임") String nickname,
    @Schema(description = "캔버스 번호", example = "1") int canvasIndex,
    @Schema(description = "자동 제출 파트", example = "BODY") RelayDrawingPart part,
    @Schema(description = "배정 상태", example = "AUTO_SUBMITTED") RelayAssignmentStatus assignmentStatus,
    @Schema(description = "빈 제출 여부", example = "true") boolean empty,
    @Schema(description = "자동 제출 시각", example = "2026-05-05T14:01:16") LocalDateTime submittedAt) {

    public static RelayRoomPartAutoSubmittedEventResponse from(String roomCode, String nickname,
        RelayRoomAssignment assignment) {
        return new RelayRoomPartAutoSubmittedEventResponse(roomCode, assignment.assignedUserUuid(), nickname,
            assignment.canvasIndex(), assignment.part(), assignment.status(), assignment.empty(),
            assignment.submittedAt());
    }
}
