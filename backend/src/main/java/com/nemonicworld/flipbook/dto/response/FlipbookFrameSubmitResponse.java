package com.nemonicworld.flipbook.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nemonicworld.flipbook.entity.FlipbookFrameAssignmentStatus;
import com.nemonicworld.flipbook.redis.FlipbookFrameAssignment;
import com.nemonicworld.flipbook.redis.FlipbookRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "플립북 프레임 제출 응답")
public record FlipbookFrameSubmitResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "제출 라운드", example = "2") int round,
    @Schema(description = "제출 플립북 번호", example = "1") int flipbookIndex,
    @Schema(description = "제출 프레임 번호", example = "2") int frameIndex,
    @Schema(description = "제출 후 배정 상태", example = "SUBMITTED") FlipbookFrameAssignmentStatus assignmentStatus,
    @Schema(description = "제출 파일 ID", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479") String fileId,
    @Schema(description = "제출 이미지 객체 키", example = "uploads/flipbook/2026/05/08/.../frame.png") String objectKey,
    @Schema(description = "제출 이미지 URL", nullable = true) String frameUrl,
    @Schema(description = "제출 시각", example = "2026-05-08T14:00:31") LocalDateTime submittedAt,
    @Schema(description = "기존 제출 재사용 여부", example = "false") boolean alreadySubmitted,
    @Schema(description = "제출 라운드 완료 여부", example = "true") boolean currentRoundCompleted,
    @Schema(description = "제출 완료 수", example = "2") int submittedCount,
    @Schema(description = "현재 라운드 전체 배정 수", example = "2") int totalCount,
    @Schema(description = "다음 라운드 또는 종료 상태로 전환되었는지 여부", example = "true") boolean advanced,
    @Schema(description = "다음 라운드. 마지막 라운드 완료 시 null입니다.", nullable = true, example = "3") Integer nextRound,
    @Schema(description = "다음 라운드 시작 시각", nullable = true) LocalDateTime nextRoundStartedAt,
    @Schema(description = "다음 라운드 마감 시각", nullable = true) LocalDateTime nextRoundDeadlineAt,
    @Schema(description = "전체 라운드 완료 여부", example = "false") boolean allRoundsCompleted,
    @Schema(description = "제출 후 방 상태", example = "PLAYING") FlipbookRoomStatus roomStatus,
    @JsonIgnore @Schema(hidden = true) String userUuid, @JsonIgnore @Schema(hidden = true) String nickname) {

    public static FlipbookFrameSubmitResponse from(String roomCode, FlipbookFrameAssignment assignment, String frameUrl,
        boolean alreadySubmitted, boolean currentRoundCompleted, int submittedCount, int totalCount, boolean advanced,
        Integer nextRound, LocalDateTime nextRoundStartedAt, LocalDateTime nextRoundDeadlineAt,
        boolean allRoundsCompleted, FlipbookRoomStatus roomStatus, String userUuid, String nickname) {
        return new FlipbookFrameSubmitResponse(roomCode, assignment.round(), assignment.flipbookIndex(),
            assignment.frameIndex(), assignment.status(), assignment.fileId(), assignment.objectKey(), frameUrl,
            assignment.submittedAt(), alreadySubmitted, currentRoundCompleted, submittedCount, totalCount, advanced,
            nextRound, nextRoundStartedAt, nextRoundDeadlineAt, allRoundsCompleted, roomStatus, userUuid, nickname);
    }
}
