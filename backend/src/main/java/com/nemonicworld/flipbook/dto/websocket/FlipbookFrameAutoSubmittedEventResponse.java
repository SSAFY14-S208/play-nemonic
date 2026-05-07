package com.nemonicworld.flipbook.dto.websocket;

import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "플립북 프레임 자동 제출 WebSocket 이벤트")
public record FlipbookFrameAutoSubmittedEventResponse(@Schema(description = "방 코드") String roomCode,
    @Schema(description = "자동 제출 사용자 UUID") String userUuid, @Schema(description = "자동 제출 사용자 닉네임") String nickname,
    @Schema(description = "플립북 번호", example = "1") int flipbookIndex,
    @Schema(description = "프레임 번호", example = "2") int frameIndex,
    @Schema(description = "자동 제출 라운드", example = "2") int round,
    @Schema(description = "배정 상태", example = "AUTO_SUBMITTED") FlipbookFrameAssignmentStatus assignmentStatus,
    @Schema(description = "빈 제출 여부", example = "true") boolean empty,
    @Schema(description = "자동 제출 시각", example = "2026-05-08T14:01:16") LocalDateTime submittedAt) {

    public static FlipbookFrameAutoSubmittedEventResponse from(String roomCode, String nickname,
        FlipbookFrameAssignment assignment) {
        return new FlipbookFrameAutoSubmittedEventResponse(roomCode, assignment.assignedUserUuid(), nickname,
            assignment.flipbookIndex(), assignment.frameIndex(), assignment.round(), assignment.status(),
            assignment.empty(), assignment.submittedAt());
    }
}
