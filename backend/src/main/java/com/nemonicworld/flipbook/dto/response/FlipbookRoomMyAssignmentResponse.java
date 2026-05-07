package com.nemonicworld.flipbook.dto.response;

import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "내 플립북 현재 프레임 배정 조회 응답")
public record FlipbookRoomMyAssignmentResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "현재 라운드", example = "2") int currentRound,
    @Schema(description = "전체 라운드 수", example = "4") int totalRounds,
    @Schema(description = "현재 사용자가 그릴 플립북 번호", example = "1") int flipbookIndex,
    @Schema(description = "현재 사용자가 그릴 프레임 번호", example = "1") int frameIndex,
    @Schema(description = "현재 배정 상태", example = "PENDING") FlipbookFrameAssignmentStatus assignmentStatus,
    @Schema(description = "라운드별 제한 시간(초)", example = "45") int timeLimitSeconds,
    @Schema(description = "현재 라운드 시작 시각", example = "2026-05-07T14:00:00") LocalDateTime roundStartedAt,
    @Schema(description = "현재 라운드 마감 시각", example = "2026-05-07T14:00:45") LocalDateTime roundDeadlineAt,
    @Schema(description = "서버 기준 남은 시간(초)", example = "32") long remainingSeconds,
    @Schema(description = "이전 프레임 오니언 스킨 힌트") FlipbookFrameHintResponse hint) {

    public static FlipbookRoomMyAssignmentResponse from(FlipbookRoomState roomState, FlipbookFrameAssignment assignment,
        long remainingSeconds, FlipbookFrameHintResponse hint) {
        return new FlipbookRoomMyAssignmentResponse(roomState.roomCode(), roomState.currentRound(),
            roomState.totalRounds(), assignment.flipbookIndex(), assignment.frameIndex(), assignment.status(),
            roomState.timeLimitSeconds(), roomState.roundStartedAt(), roomState.roundDeadlineAt(), remainingSeconds,
            hint);
    }
}
