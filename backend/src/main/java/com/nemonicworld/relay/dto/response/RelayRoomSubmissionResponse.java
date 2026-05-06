package com.nemonicworld.relay.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nemonicworld.relay.entity.RelayAssignmentStatus;
import com.nemonicworld.relay.entity.RelayDrawingPart;
import com.nemonicworld.relay.redis.RelayRoomAssignment;
import com.nemonicworld.relay.entity.RelayRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "릴레이 현재 파트 제출 응답")
public record RelayRoomSubmissionResponse(@Schema(description = "방 코드", example = "AB3K9Q") String roomCode,
    @Schema(description = "제출 캔버스 번호", example = "1") int canvasIndex,
    @Schema(description = "제출 파트", example = "BODY") RelayDrawingPart part,
    @Schema(description = "제출 후 배정 상태", example = "SUBMITTED") RelayAssignmentStatus assignmentStatus,
    @Schema(description = "원본 이미지 object key", example = "relay/tmp/AB3K9Q/1/body.png") String drawingObjectKey,
    @Schema(description = "힌트 object key", nullable = true, example = "relay/tmp/...") String hintObjectKey,
    @Schema(description = "제출 시각", example = "2026-05-05T14:00:31") LocalDateTime submittedAt,
    @Schema(description = "기존 제출 재사용 여부", example = "false") boolean alreadySubmitted,
    @Schema(description = "제출 파트 완료 여부", example = "false") boolean currentPartCompleted,
    @Schema(description = "제출 완료 수", example = "2") int submittedCount,
    @Schema(description = "전체 배정 수", example = "3") int totalCount,
    @Schema(description = "다음 단계 전환 여부", example = "true") boolean advanced,
    @Schema(description = "다음 파트", nullable = true, example = "LEGS") RelayDrawingPart nextPart,
    @Schema(description = "다음 파트 시작 시각") LocalDateTime nextPartStartedAt,
    @Schema(description = "다음 파트 마감 시각") LocalDateTime nextPartDeadlineAt,
    @Schema(description = "전체 파트 완료 여부", example = "false") boolean allPartsCompleted,
    @Schema(description = "제출 후 방 상태", example = "PLAYING") RelayRoomStatus roomStatus,
    @JsonIgnore @Schema(hidden = true) String userUuid, @JsonIgnore @Schema(hidden = true) String nickname) {

    public static RelayRoomSubmissionResponse from(String roomCode, RelayRoomAssignment assignment,
        boolean alreadySubmitted, boolean currentPartCompleted, int submittedCount, int totalCount, String userUuid,
        String nickname, boolean advanced, RelayDrawingPart nextPart, LocalDateTime nextPartStartedAt,
        LocalDateTime nextPartDeadlineAt, boolean allPartsCompleted, RelayRoomStatus roomStatus) {
        return new RelayRoomSubmissionResponse(roomCode, assignment.canvasIndex(), assignment.part(),
            assignment.status(), assignment.objectKey(), assignment.hintObjectKey(), assignment.submittedAt(),
            alreadySubmitted, currentPartCompleted, submittedCount, totalCount, advanced, nextPart, nextPartStartedAt,
            nextPartDeadlineAt, allPartsCompleted, roomStatus, userUuid, nickname);
    }
}
